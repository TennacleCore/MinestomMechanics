package io.github.term4.minestommechanics.tracking;

import io.github.term4.minestommechanics.util.TickClock;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.Player;
import net.minestom.server.tag.Tag;
import net.minestom.server.timer.TaskSchedule;
import org.jetbrains.annotations.Nullable;

/**
 * Authority for an entity's ground/air timeline: how long it has been airborne and whether it is in an
 * upward-launched arc (a jump or knockback boost) versus a ledge walk-off. Runs a per-tick task so
 * these facts share one clock; player vertical motion comes from {@link MovementTracker} and landing
 * lookahead from {@link LandingPredictor}.
 */
public final class GroundTracker {

    /** Tick when entity was last on ground. Used to compute ticks-in-air for gravity-predicted velocity. */
    public static final Tag<Long> LAST_GROUND_TICK = Tag.Transient("mm:last-ground-tick");
    private static final Tag<Boolean> LAUNCHED = Tag.Transient("mm:launched");

    public GroundTracker() {}

    /**
     * Starts the ground tracker. Runs every tick to update the ground/air timeline and launch latch.
     */
    public void start() {
        MinecraftServer.getSchedulerManager()
                .buildTask(this::tick)
                .repeat(TaskSchedule.tick(1))
                .schedule();
    }

    private void tick() {
        long now = TickClock.now();
        for (Player p : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
            // Launch state for gravity prediction: any upward motion while airborne (a jump or a knockback
            // boost, including a mid-air re-launch) latches a launch arc; landing clears it so a later
            // walk-off starts unlaunched. Flight is not a ballistic arc, so it also clears. Tracked on the
            // same clock as ticks-in-air so the two stay phase-aligned for VelocityRule.gravityPredicted.
            if (p.isOnGround() || LandingPredictor.willLandSoon(p)) {
                p.setTag(LAST_GROUND_TICK, now);
                p.removeTag(LAUNCHED);
            } else if (p.isFlying()) {
                p.removeTag(LAUNCHED);
            } else if (MovementTracker.tracked(p).y() > 0) {
                p.setTag(LAUNCHED, true);
            }
        }
    }

    /**
     * Ticks since entity last touched ground. 0 when on ground (we update LAST_GROUND_TICK every tick)
     * or no history. Intentionally does NOT check isOnGround()—it can be 1 tick late from the client,
     * which would wrongly zero out gravity dampening at the moment of air hits.
     */
    public static int ticksInAir(Entity entity) {
        if (entity == null) return 0;
        Long last = entity.getTag(LAST_GROUND_TICK);
        if (last == null) return 0;
        return (int) Math.max(0, TickClock.now() - last);
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

    /**
     * Whether the entity is effectively on the ground: Minestom's {@link Entity#isOnGround()} flag, or
     * (when {@code predictLanding} is true) the landing lookahead. Pass {@code false} to force the plain
     * Minestom check.
     */
    public static boolean isGrounded(@Nullable Entity entity, boolean predictLanding) {
        if (entity == null) return false;
        if (entity.isOnGround()) return true;
        return predictLanding && LandingPredictor.willLandSoon(entity);
    }

    /**
     * Whether the entity is falling: descending and not on the ground. Vertical motion comes from
     * {@link MovementTracker#tracked(Entity)} (position-delta) for players, since Minestom's server-side
     * {@code getVelocity()} does not reflect a player's client-driven up/down motion.
     *
     * <p>A flying player is never falling, even when moving downward. Other states where a player
     * cannot be falling are not handled here yet (TODO): in water, and climbing (on a ladder/vine).
     */
    public static boolean isFalling(@Nullable Entity entity, boolean predictLanding) {
        if (entity == null) return false;
        if (entity instanceof Player p && p.isFlying()) return false;
        // Grounded check honors the landing lookahead when enabled, else plain isOnGround;
        // this also keeps falling/grounded mutually exclusive.
        if (isGrounded(entity, predictLanding)) return false;
        // TODO: also not falling while in water, climbing (ladder/vine), cobweb, and maybe other.
        return MovementTracker.tracked(entity).y() < 0;
    }

    /** Convenience falling check using only Minestom's onGround flag (no landing prediction). */
    public static boolean isFalling(@Nullable Entity entity) {
        return isFalling(entity, false);
    }
}
