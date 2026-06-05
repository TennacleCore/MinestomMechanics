package io.github.term4.minestommechanics.tracking;

import io.github.term4.minestommechanics.util.TickClock;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.Player;
import net.minestom.server.event.EventFilter;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.PlayerMoveEvent;
import net.minestom.server.event.trait.PlayerEvent;
import net.minestom.server.tag.Tag;
import net.minestom.server.timer.TaskSchedule;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Single authority for an entity's ground/air timeline and per-tick motion. A {@link PlayerMoveEvent}
 * listener anchors the air clock to the client's rising move-packet (the client's true jump tick), so
 * {@link #ticksInAir(Entity)} tracks the real air-time directly instead of re-simulating it. Vanilla's
 * folded {@code this.motY} is a server-side gravity re-sim seeded ~2 ticks later off the server's
 * {@code onGround} flag, which is why the knockback arc re-applies {@link VelocityContext#VANILLA_LAUNCH_OFFSET}.
 * A per-tick task snapshots position for {@link #positionDelta(Entity)}:
 * <ul>
 *   <li>{@link #ticksInAir(Entity)} - ticks since the move-packet that left the ground (drives the gravity arc),</li>
 *   <li>{@link #launched(Entity)} - whether the entity is in an upward-launched arc (jump or knockback
 *       boost) versus a ledge walk-off,</li>
 *   <li>{@link #recentJump(Entity)} - the launch stamp (yaw + sprint) for the sprint-jump impulse,</li>
 *   <li>{@link #positionDelta(Entity)} - position-delta velocity (blocks/tick).</li>
 * </ul>
 *
 * <p>Ground state is the client-authoritative {@link Entity#isOnGround()} flag (vanilla trusts the
 * client); there is no server-side landing prediction.
 */
public final class GroundTracker {

    /**
     * Server tick of the client's rising move-packet (its true jump tick); the gravity-arc anchor. Vanilla's
     * folded {@code this.motY} re-sim seeds ~2 ticks after this, hence {@link VelocityContext#VANILLA_LAUNCH_OFFSET}.
     */
    private static final Tag<Long> AIR_START_TICK = Tag.Transient("mm:air-start-tick");
    private static final Tag<Boolean> LAUNCHED = Tag.Transient("mm:launched");
    private static final Tag<PrevState> PREV_STATE = Tag.Transient("mm:prev-state");
    private static final Tag<MovePrev> MOVE_PREV = Tag.Transient("mm:move-prev");
    private static final Tag<LaunchStamp> LAUNCH_STAMP = Tag.Transient("mm:launch-stamp");
    /**
     * Server-side horizontal {@code motX/motZ} (blocks/tick) anchored at the last ground/air transition, plus the
     * tick and ground state it has held since. Read lazily, it bleeds by vanilla friction (air {@code x0.91},
     * ground {@code x0.546}) over the elapsed ticks - identical to applying friction once per server tick, but
     * computed only at the next transition instead of polling every tick. A launch folds the {@code bF()} 0.2
     * boost onto this, so the takeoff carries the real residual (0.2 from rest, building toward ~0.248
     * mid-bunny-hop) instead of a fixed magic number.
     */
    private static final Tag<MotState> MOT_H = Tag.Transient("mm:mot-h");

    /** Previous-tick position snapshot backing position-delta velocity (blocks/tick). */
    private record PrevState(Pos pos, boolean onGround) {}
    /** Previous move-packet vertical + ground flag, for packet-granular ground/air transition detection. */
    private record MovePrev(double y, boolean onGround) {}
    /**
     * Launch impulse origin: server tick, facing yaw, whether sprinting at takeoff, and {@code seedH} - the
     * actual horizontal {@code motX/motZ} (blocks/tick) at takeoff (carried ground residual + the {@code bF()}
     * boost). The gravity arc bleeds {@code seedH} by air friction, so a hit folds in the real takeoff velocity.
     */
    private record LaunchStamp(long tick, double yaw, boolean sprinting, Vec seedH) {}
    /**
     * Server-side horizontal {@code motX/motZ} anchored at the last transition: {@code motH} as of {@code sinceTick},
     * bleeding by air friction while {@code airborne} else ground friction. {@link #residualAt(MotState, long)}
     * advances it to any later tick.
     */
    private record MotState(Vec motH, long sinceTick, boolean airborne) {}

    public GroundTracker() {}

    /** Starts the per-tick position snapshot that backs {@link #positionDelta(Entity)}. */
    public void start() {
        MinecraftServer.getSchedulerManager()
                .buildTask(this::tick)
                .repeat(TaskSchedule.tick(1))
                .schedule();
    }

    /**
     * Listener that anchors the air clock to the client's rising move-packet (instead of a once-per-tick
     * scan), so {@link #ticksInAir(Entity)} counts elapsed ticks from the client's own jump tick. It tracks
     * the real air-time; vanilla's server-side {@code this.motY} re-sim trails it by ~2 ticks.
     */
    public EventNode<@NotNull PlayerEvent> node() {
        EventNode<@NotNull PlayerEvent> node = EventNode.type("mm:ground-tracker", EventFilter.PLAYER);
        node.addListener(PlayerMoveEvent.class, this::onMove);
        return node;
    }

    private void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        long now = TickClock.now();
        boolean nowOnGround = e.isOnGround();
        Pos newPos = e.getNewPosition();
        // Compare against the previous move-packet (not p.isOnGround()/getPosition()) so transition
        // detection is independent of whether the event fires before or after Minestom applies the move.
        MovePrev prev = p.getTag(MOVE_PREV);
        p.setTag(MOVE_PREV, new MovePrev(newPos.y(), nowOnGround));
        if (prev == null) return; // need a baseline packet before a transition can be read

        boolean wasOnGround = prev.onGround();
        double dy = newPos.y() - prev.y();

        // On ground (or flying): the ballistic arc is not running, so clear it. This is the PRIMARY landing
        // anchor - it fires during interpretPacketQueue (serverTick phase) on the very packet whose onGround
        // flips, so it re-anchors the motX residual (bled by air friction over the flight) to ground state on
        // the exact tick the landing is processed. The tick() poll runs earlier in the cycle but on last tick's
        // ground flag, so it only ever trails this.
        if (nowOnGround || p.isFlying()) {
            if (nowOnGround) freezeOnLanding(p, now);
            p.removeTag(AIR_START_TICK);
            p.removeTag(LAUNCHED);
            return;
        }

        if (wasOnGround) {
            // Ground -> air: the client's rising packet = its true jump tick. Anchor the arc's tick-zero here and
            // fold the 0.2 boost onto the maintained motX residual. (Vanilla's folded this.motY re-sim seeds ~2
            // ticks later off the server onGround flag, which VANILLA_LAUNCH_OFFSET re-applies.)
            if (dy > 0) latchLaunch(p, now, newPos.yaw()); // rising = launched, else walk-off
            p.setTag(AIR_START_TICK, now);
        } else {
            // TODO: Remove / change how this works
            // Already airborne: ensure an anchor exists (e.g. joined mid-air) and latch a mid-air re-launch.
            if (p.getTag(AIR_START_TICK) == null) p.setTag(AIR_START_TICK, now);
            if (dy > 0 && !Boolean.TRUE.equals(p.getTag(LAUNCHED))) latchLaunch(p, now, newPos.yaw());
        }
    }

    /**
     * Latches a launch: folds the {@code bF()} 0.2 boost onto the {@code motX} residual (the anchored value bled
     * by friction up to {@code now} - ground friction if grounded since landing, air friction for a mid-air
     * re-launch) and freezes that as the takeoff seed the gravity arc bleeds. So the seed is 0.2 off a standstill
     * and builds toward ~0.248 mid-bunny-hop, then re-anchors as airborne for the new flight.
     */
    private static void latchLaunch(Player p, long now, double yaw) {
        boolean sprinting = p.isSprinting();
        Vec residual = residualAt(p.getTag(MOT_H), now);
        Vec boost = sprinting ? VelocityContext.sprintJumpImpulse(yaw) : Vec.ZERO;
        Vec seedH = residual.add(boost);
        p.setTag(MOT_H, new MotState(seedH, now, true));
        p.setTag(LAUNCHED, true);
        p.setTag(LAUNCH_STAMP, new LaunchStamp(now, yaw, sprinting, seedH));
    }

    /**
     * The anchored residual bled forward to {@code now}: {@code motH x friction^(now - sinceTick)}, friction
     * being air ({@code x0.91}) or ground ({@code x0.546}) per the held state, then vanilla's near-zero clamp.
     * Equivalent to one friction step (and {@code m()} clamp) per server tick, but evaluated lazily at the next
     * transition instead of polling every tick.
     */
    private static Vec residualAt(MotState s, long now) {
        if (s == null) return Vec.ZERO;
        int ticks = (int) Math.max(0, now - s.sinceTick());
        double friction = s.airborne() ? VelocityContext.AIR_FRICTION_H : VelocityContext.GROUND_FRICTION_H;
        Vec decayed = s.motH().mul(Math.pow(friction, ticks));
        // Vanilla m() zeroes motX/motZ < 0.005 each tick; for a monotonically-bleeding residual that is the same
        // as clamping the final value per component, so a stale residual snaps to 0 (vanilla 0.00748 -> 0) rather
        // than trailing dust (0.004 -> 0.0004 -> ...) into the next seed.
        double c = VelocityContext.NEAR_ZERO_CLAMP;
        return new Vec(Math.abs(decayed.x()) < c ? 0.0 : decayed.x(), 0, Math.abs(decayed.z()) < c ? 0.0 : decayed.z());
    }

    /**
     * At an air-&gt;ground transition, re-anchor the residual as grounded: bleed the airborne value to {@code now}
     * (one air-friction step per flight tick) and stamp it ground state so the gap to the next jump bleeds by
     * ground friction. A no-op if already grounded.
     */
    private static void freezeOnLanding(Player p, long now) {
        MotState s = p.getTag(MOT_H);
        if (s == null || !s.airborne()) return;
        p.setTag(MOT_H, new MotState(residualAt(s, now), now, false));
    }

    private void tick() {
        for (Player p : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
            boolean onGround = p.isOnGround();
            // FALLBACK only, and deliberately a tick behind: this scheduler task runs in processTick(), before
            // serverTick() drains move packets, so onGround here reflects the PREVIOUS tick - the onMove landing
            // branch (running in packet processing) always anchors a real landing first. This catches the
            // leftovers onMove never sees (status-only onGround packets with no PlayerMoveEvent). freezeOnLanding
            // is idempotent, so it no-ops whenever onMove already anchored.
            if (onGround) {
                freezeOnLanding(p, TickClock.now());
                p.removeTag(AIR_START_TICK);
                p.removeTag(LAUNCHED);
            }
            // Per-tick position snapshot backing positionDelta() (blocks/tick).
            p.setTag(PREV_STATE, new PrevState(p.getPosition(), onGround));
        }
    }

    /**
     * Ticks since the client's rising move-packet (the gravity-arc clock) - elapsed-tick counting from the
     * client's true jump tick, so it is ping-invariant and matches the client's own air-tick count. 0 when on
     * the ground or with no air anchor. Vanilla's folded {@code this.motY} re-sim trails this by ~2 ticks
     * ({@link VelocityContext#VANILLA_LAUNCH_OFFSET}).
     */
    public static int ticksInAir(Entity entity) {
        if (entity == null) return 0;
        Long start = entity.getTag(AIR_START_TICK);
        if (start == null) return 0;
        return (int) Math.max(0, TickClock.now() - start);
    }

    /**
     * Whether the entity is in an upward-launched arc (a jump or a knockback boost) rather than a ledge
     * walk-off. Latched true on any upward motion while airborne - so it survives the descending half of
     * the arc and covers mid-air re-launches - and cleared when the entity lands or starts flying. A plain
     * walk-off never rises, so it stays unlaunched.
     */
    public static boolean launched(Entity entity) {
        return entity instanceof Player p && Boolean.TRUE.equals(p.getTag(LAUNCHED));
    }

    /** Launch origin (yaw + sprint + takeoff horizontal seed) while in a launch arc, or {@code null}. */
    public static @Nullable VelocityContext.JumpInfo recentJump(Entity entity) {
        if (!(entity instanceof Player p) || !launched(p)) return null;
        LaunchStamp s = p.getTag(LAUNCH_STAMP);
        return s == null ? null : new VelocityContext.JumpInfo(s.yaw(), s.sprinting(), s.seedH());
    }

    // TODO: Move this or rename this whole class, this is a velocity method
    /** Position-delta velocity (blocks/tick); players via the per-tick snapshot, others via entity velocity. */
    public static Vec positionDelta(Entity entity) {
        if (entity instanceof Player p) {
            PrevState prev = p.getTag(PREV_STATE);
            if (prev == null) return Vec.ZERO;
            Pos pos = p.getPosition();
            Pos last = prev.pos();
            return new Vec(pos.x() - last.x(), pos.y() - last.y(), pos.z() - last.z());
        }
        return entity.getVelocity();
    }

    // TODO: Remove and remove references, we no longer PREDICT ground state, we just trust the client. This is a relic
    /** Whether the entity is on the ground, using Minestom's client-authoritative {@link Entity#isOnGround()}. */
    public static boolean isGrounded(@Nullable Entity entity) {
        return entity != null && entity.isOnGround();
    }

    /**
     * Whether the entity is falling: airborne and descending. Vertical motion comes from
     * {@link #positionDelta(Entity)} (position-delta) for players, since Minestom's server-side
     * {@code getVelocity()} does not reflect a player's client-driven up/down motion.
     *
     * <p>A flying player is never falling, even when moving downward. Other states where a player
     * cannot be falling are not handled here yet (TODO): in water, and climbing (on a ladder/vine).
     */
    public static boolean isFalling(@Nullable Entity entity) {
        if (entity == null) return false;
        if (entity instanceof Player p && p.isFlying()) return false;
        if (entity.isOnGround()) return false;
        // TODO: also not falling while in water, climbing (ladder/vine), cobweb, and maybe other.
        return positionDelta(entity).y() < 0;
    }
}
