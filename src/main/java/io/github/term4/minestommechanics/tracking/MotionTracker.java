package io.github.term4.minestommechanics.tracking;

import io.github.term4.minestommechanics.util.TickClock;
import net.minestom.server.MinecraftServer;
import net.minestom.server.collision.CollisionUtils;
import net.minestom.server.collision.PhysicsResult;
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
 * {@code onGround} flag, which is why the knockback arc re-applies {@link VelocityRule#VANILLA_LAUNCH_OFFSET}.
 * The same listener records each move-packet delta for {@link #positionDelta(Entity)}:
 * <ul>
 *   <li>{@link #ticksInAir(Entity)} - ticks since the move-packet that left the ground (drives the gravity arc),</li>
 *   <li>{@link #launched(Entity)} - whether the entity is in an upward-launched arc (jump or knockback
 *       boost) versus a ledge walk-off,</li>
 *   <li>{@link #recentJump(Entity)} - the launch stamp (yaw + sprint) for the sprint-jump impulse,</li>
 *   <li>{@link #positionDelta(Entity)} - move-delta velocity (blocks/tick), the client's last reported motion.</li>
 * </ul>
 *
 * <p>Ground state goes through a {@link GroundRule} ({@link #isGrounded}, {@link #isFalling}, and the air clock all
 * resolve one via {@link #resolveGroundRule}: a per-entity {@link #setGroundRule override}, else a velocity-mode's
 * {@link ArcSpec#groundRule()}, else {@link GroundRule#DEFAULT}). The default is vanilla {@code Entity.move()}
 * collision ({@link #isGroundedByCollision}: the client flag OR a server-side sweep down the client's reported
 * fall), so a laggy client that has truly landed reads grounded before its {@code onGround} packet arrives, instead
 * of the bare {@link Entity#isOnGround()} flag trailing the landing and leaving the arc reconstructing a stale
 * descent. The sweep is keyed off the reported {@link #positionDelta}, never the knockback velocity arc - ground
 * state is a position prediction, independent of the vertical KB curve.
 */
public final class MotionTracker {

    /**
     * Server tick of the client's rising move-packet (its true jump tick); the gravity-arc anchor. Vanilla's
     * folded {@code this.motY} re-sim seeds ~2 ticks after this, hence {@link VelocityRule#VANILLA_LAUNCH_OFFSET}.
     */
    private static final Tag<Long> AIR_START_TICK = Tag.Transient("mm:air-start-tick");
    private static final Tag<Boolean> LAUNCHED = Tag.Transient("mm:launched");
    private static final Tag<Vec> MOVE_VELOCITY = Tag.Transient("mm:move-velocity");
    private static final Tag<MovePrev> MOVE_PREV = Tag.Transient("mm:move-prev");
    private static final Tag<LaunchStamp> LAUNCH_STAMP = Tag.Transient("mm:launch-stamp");
    /** Per-entity {@link GroundRule} override, highest precedence in {@link #resolveGroundRule}; unset -> the caller's fallback. */
    private static final Tag<GroundRule> GROUND_RULE = Tag.Transient("mm:ground-rule");
    /**
     * Server-side horizontal {@code motX/motZ} (blocks/tick) anchored at the last ground/air transition, plus the
     * tick and ground state it has held since. Read lazily, it bleeds by vanilla friction (air {@code x0.91},
     * ground {@code x0.546}) over the elapsed ticks - identical to applying friction once per server tick, but
     * computed only at the next transition instead of polling every tick. A launch folds the {@code bF()} 0.2
     * boost onto this, so the takeoff carries the real residual (0.2 from rest, building toward ~0.248
     * mid-bunny-hop) instead of a fixed magic number.
     */
    private static final Tag<MotState> MOT_H = Tag.Transient("mm:mot-h");

    /** Previous move-packet position + ground flag: packet-granular transition detection, and the move-delta velocity. */
    private record MovePrev(double x, double y, double z, boolean onGround) {}
    /**
     * Launch impulse origin: server tick, facing yaw, whether sprinting at takeoff, and {@code seedH} - the
     * actual horizontal {@code motX/motZ} (blocks/tick) at takeoff (carried ground residual + the {@code bF()}
     * boost). The gravity arc bleeds {@code seedH} by air friction, so a hit folds in the real takeoff velocity.
     */
    private record LaunchStamp(long tick, double yaw, boolean sprinting, Vec seedH) {}
    /**
     * Server-side horizontal {@code motX/motZ} anchored at the last transition: {@code motH} as of {@code sinceTick},
     * bleeding by air friction while {@code airborne} else ground friction. {@link #residualAt(MotState, long, Physics)}
     * advances it to any later tick.
     */
    private record MotState(Vec motH, long sinceTick, boolean airborne) {}

    /**
     * Launch origin exposed to {@link VelocityRule}s: facing yaw, whether sprinting at takeoff, and {@code seedH} -
     * the actual horizontal {@code this.motX/motZ} (blocks/tick) at the launch tick (the bled-over ground residual
     * plus the {@code bF()} boost). The gravity arc seeds from {@code seedH} and bleeds it by air friction, so a
     * hit's horizontal fold matches whatever the player really took off with (0.2 from rest, ~0.248 mid-bunny-hop).
     */
    public record JumpInfo(double yaw, boolean sprinting, Vec seedH) {}

    public MotionTracker() {}

    /** Starts the per-tick ground-state fallback poll (a tick behind {@link #onMove}; catches status-only onGround packets). */
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
        EventNode<@NotNull PlayerEvent> node = EventNode.type("mm:motion-tracker", EventFilter.PLAYER);
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
        p.setTag(MOVE_PREV, new MovePrev(newPos.x(), newPos.y(), newPos.z(), nowOnGround));
        if (prev == null) return; // need a baseline packet before a transition can be read

        // Move-delta velocity (b/t) straight off the client's packets. A once-per-tick getPosition() snapshot races
        // the hit (an attacker's packet often processes before the victim's move that tick, reading exactly 0), so
        // instead we keep the victim's last reported motion - the ground sweep deepens by it when the client is
        // falling faster than the reconstructed motY.
        p.setTag(MOVE_VELOCITY, new Vec(newPos.x() - prev.x(), newPos.y() - prev.y(), newPos.z() - prev.z()));

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
        Physics ph = Physics.fromEntity(p);
        boolean sprinting = p.isSprinting();
        Vec residual = residualAt(p.getTag(MOT_H), now, ph);
        Vec boost = sprinting ? ph.sprintJumpImpulse(yaw) : Vec.ZERO;
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
    private static Vec residualAt(MotState s, long now, Physics ph) {
        if (s == null) return Vec.ZERO;
        int ticks = (int) Math.max(0, now - s.sinceTick());
        double friction = s.airborne() ? ph.horizontalAirResistance() : ph.groundFriction();
        Vec decayed = s.motH().mul(Math.pow(friction, ticks));
        // Vanilla m() zeroes motX/motZ < 0.005 each tick; for a monotonically-bleeding residual that is the same
        // as clamping the final value per component, so a stale residual snaps to 0 (vanilla 0.00748 -> 0) rather
        // than trailing dust (0.004 -> 0.0004 -> ...) into the next seed.
        return new Vec(Math.abs(decayed.x()) < ph.clampX() ? 0.0 : decayed.x(), 0,
                Math.abs(decayed.z()) < ph.clampZ() ? 0.0 : decayed.z());
    }

    /**
     * At an air-&gt;ground transition, re-anchor the residual as grounded: bleed the airborne value to {@code now}
     * (one air-friction step per flight tick) and stamp it ground state so the gap to the next jump bleeds by
     * ground friction. A no-op if already grounded.
     */
    private static void freezeOnLanding(Player p, long now) {
        MotState s = p.getTag(MOT_H);
        if (s == null || !s.airborne()) return;
        p.setTag(MOT_H, new MotState(residualAt(s, now, Physics.fromEntity(p)), now, false));
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
        }
    }

    /**
     * Ticks since the client's rising move-packet (the gravity-arc clock) - elapsed-tick counting from the
     * client's true jump tick, so it is ping-invariant and matches the client's own air-tick count. 0 when on
     * the ground or with no air anchor. Vanilla's folded {@code this.motY} re-sim trails this by ~2 ticks
     * ({@link VelocityRule#VANILLA_LAUNCH_OFFSET}).
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
    public static @Nullable JumpInfo recentJump(Entity entity) {
        if (!(entity instanceof Player p) || !launched(p)) return null;
        LaunchStamp s = p.getTag(LAUNCH_STAMP);
        return s == null ? null : new JumpInfo(s.yaw(), s.sprinting(), s.seedH());
    }

    /** Move-delta velocity (blocks/tick) from the client's packets; players via {@link #MOVE_VELOCITY}, others via entity velocity. */
    public static Vec positionDelta(Entity entity) {
        if (entity instanceof Player p) {
            Vec d = p.getTag(MOVE_VELOCITY);
            return d == null ? Vec.ZERO : d;
        }
        return entity.getVelocity();
    }

    /**
     * Sets (or with {@code null} clears) a per-entity {@link GroundRule} override. It is the highest-precedence
     * source in {@link #resolveGroundRule}, so it wins over any velocity-mode {@link ArcSpec#groundRule()} and the
     * {@link GroundRule#DEFAULT} - the runtime knob for forcing one entity onto, e.g., {@link GroundRule#CLIENT}.
     */
    public static void setGroundRule(@NotNull Entity entity, @Nullable GroundRule rule) {
        if (rule == null) entity.removeTag(GROUND_RULE);
        else entity.setTag(GROUND_RULE, rule);
    }

    /**
     * Resolves the effective {@link GroundRule} for {@code entity}: a per-entity {@link #setGroundRule override}
     * wins, else {@code fallback} (e.g. a velocity-mode's {@link ArcSpec#groundRule()}), else {@link GroundRule#DEFAULT}.
     */
    public static GroundRule resolveGroundRule(@Nullable Entity entity, @Nullable GroundRule fallback) {
        if (entity != null) {
            GroundRule tagged = entity.getTag(GROUND_RULE);
            if (tagged != null) return tagged;
        }
        return fallback != null ? fallback : GroundRule.DEFAULT;
    }

    /**
     * Whether the entity is on the ground per its resolved {@link GroundRule} ({@link #resolveGroundRule} with no
     * caller fallback: a per-entity override, else {@link GroundRule#DEFAULT}). The default is vanilla
     * {@code Entity.move()} collision, so a laggy victim that has truly landed reads grounded even before its
     * {@code onGround} packet arrives - not the bare {@link Entity#isOnGround()} flag.
     */
    public static boolean isGrounded(@Nullable Entity entity) {
        return resolveGroundRule(entity, null).isOnGround(entity);
    }

    /**
     * Server-side ground test mirroring vanilla {@code Entity.move()}: it sweeps the player's bounding box down by
     * its reported fall and reports whether a block clamps that descent - vanilla's
     * {@code verticalCollisionBelow = (delta.y != collide(delta).y) && delta.y < 0}. The sweep magnitude is the
     * client's reported {@link #positionDelta} (the real move-delta), floored at {@code floor} (one gravity step) and
     * plus {@code margin}. This is a <em>position</em> prediction, deliberately independent of the knockback velocity
     * arc ({@link #predictedFallVy}) - keying it off the reconstructed fall would let a juggled victim's deep air-clock
     * value ground them metres up (float), then reset the clock and re-pop them forever. Reading the server's own
     * position + world geometry, it still catches a laggy victim that has truly landed while its {@code onGround}
     * packet is in flight (the case the bare {@link Entity#isOnGround()} flag misses), at least to within the reported
     * fall depth. A victim whose reported motion is rising ({@code positionDelta.y > 0}) is never grounded (vanilla
     * {@code delta.y < 0}). Non-players fall through to {@link Entity#isOnGround()}. {@code floor}/{@code margin} are
     * magnitudes (downward).
     */
    public static boolean isGroundedByCollision(@Nullable Entity entity, double floor, double margin) {
        if (entity == null || entity.getInstance() == null) return false;
        if (!(entity instanceof Player)) return entity.isOnGround(); // server physics is authoritative for these
        return groundedBySweep(entity, floor, margin);
    }

    /**
     * {@link #isGroundedByCollision(Entity, double, double)} with the probe knobs taken from {@code spec}: its
     * {@link GroundSpec#floor()} ({@code null} -> one gravity step, {@code gravity * verticalAirResistance}, from the
     * spec's {@link GroundSpec#physics()} else the entity's live physics) and {@link GroundSpec#margin()}. This is
     * what {@link GroundRule#collision(GroundSpec)} sweeps with.
     */
    public static boolean isGroundedByCollision(@Nullable Entity entity, @NotNull GroundSpec spec) {
        if (entity == null || entity.getInstance() == null) return false;
        if (!(entity instanceof Player)) return entity.isOnGround(); // server physics is authoritative for these
        Physics ph = spec.physics() != null ? spec.physics() : Physics.fromEntity(entity);
        double floor = spec.floor() != null ? spec.floor() : ph.gravity() * ph.verticalAirResistance();
        return groundedBySweep(entity, floor, spec.margin());
    }

    /**
     * The downward box sweep behind {@link #isGroundedByCollision}: descend by the client's reported
     * {@link #positionDelta} fall, at least {@code floor} and plus {@code margin}, and report whether a block clamps
     * it. Bails when the reported motion is rising (jump / boost), never grounded (vanilla {@code delta.y < 0}). The
     * probe is the <em>reported</em> fall only - never {@link #predictedFallVy} - so ground state stays a pure
     * position prediction, decoupled from the vertical KB curve. Caller has already ruled out null/non-players.
     */
    private static boolean groundedBySweep(Entity entity, double floor, double margin) {
        double dy = positionDelta(entity).y();
        if (dy > 0) return false; // reported motion rising (jump / boost) - never grounded (vanilla delta.y < 0)
        double probe = Math.min(dy, -Math.abs(floor)) - Math.abs(margin);
        PhysicsResult r = CollisionUtils.handlePhysics(entity, new Vec(0, probe, 0));
        return r.isOnGround();
    }

    /**
     * Reconstructed vertical fall velocity (the {@link VelocityRule} arc's vertical seed) for the entity's current
     * air-time - closed-form free-fall {@code v0*s^t - g*s*(1 - s^t)/(1 - s)} seeded at {@link Physics#jumpVelocity()}
     * when {@link #launched} (over {@code ticksInAir + }{@link VelocityRule#VANILLA_LAUNCH_OFFSET}) else from rest
     * (over {@code ticksInAir + 1}), clamped to {@code [terminalVy, jumpVelocity]}. The air clock free-runs through a
     * combo (never re-anchored per hit), so this is the curve the vertical knockback folds. <strong>Not</strong> used
     * for ground detection - that is a separate position prediction ({@link #groundedBySweep}).
     */
    private static double predictedFallVy(Entity entity, Physics ph) {
        boolean launched = launched(entity);
        int ticks = launched ? ticksInAir(entity) + VelocityRule.VANILLA_LAUNCH_OFFSET : ticksInAir(entity) + 1;
        double g = ph.gravity();
        double s = ph.verticalAirResistance();
        double v0 = launched ? ph.jumpVelocity() : 0.0;
        double vy;
        if (ticks <= 0) {
            vy = -g * s;
        } else {
            double sp = Math.pow(s, ticks);
            vy = v0 * sp - g * s * (1 - sp) / (1 - s);
        }
        return Math.max(ph.terminalVy(), Math.min(ph.jumpVelocity(), vy));
    }

    /**
     * {@link #predictedFallVy(Entity, Physics)} seeded from the entity's live physics: the reconstructed fall the
     * velocity arc folds for the entity's current air-time, exposed for diagnostics/logging. Compare it against
     * {@link #positionDelta} (the real move-delta) to see how far the folded fall has diverged from the victim's
     * actual reported motion.
     */
    public static double predictedFallVy(@Nullable Entity entity) {
        if (entity == null) return 0;
        return predictedFallVy(entity, Physics.fromEntity(entity));
    }

    /**
     * Whether the entity is falling: airborne (per its resolved {@link GroundRule}, so a laggy victim that has truly
     * landed is not "falling") and descending. Vertical motion comes from {@link #positionDelta(Entity)}
     * (position-delta) for players, since Minestom's server-side {@code getVelocity()} does not reflect a player's
     * client-driven up/down motion.
     *
     * <p>A flying player is never falling, even when moving downward. Other states where a player
     * cannot be falling are not handled here yet (TODO): in water, and climbing (on a ladder/vine).
     */
    public static boolean isFalling(@Nullable Entity entity) {
        if (entity == null) return false;
        if (entity instanceof Player p && p.isFlying()) return false;
        if (isGrounded(entity)) return false;
        // TODO: also not falling while in water, climbing (ladder/vine), cobweb, and maybe other.
        return positionDelta(entity).y() < 0;
    }
}
