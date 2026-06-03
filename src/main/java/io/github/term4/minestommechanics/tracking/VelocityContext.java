package io.github.term4.minestommechanics.tracking;

import net.minestom.server.ServerFlag;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import org.jetbrains.annotations.Nullable;

/**
 * Rich, public face that {@link VelocityRule} implementations compose - the velocity analog of
 * {@code AttackEvent} for {@code CriticalRule}. Exposes the tracked state, raw entity velocity,
 * air-time, launch latch, recent-jump info, and vanilla physics constants, so a rule written
 * outside the library has the same primitives the built-ins use.
 *
 * <p>Lives in the tracking package so it can read {@link MovementTracker}'s package-private
 * accessors while remaining the only public surface a rule needs.
 */
public final class VelocityContext {

    // Vanilla physics constants (single canonical home; tracker + rules both read these).
    public static final double JUMP_Y = 0.42;
    public static final double GRAVITY_PER_TICK = 0.08;
    public static final double GRAVITY_SCALE = 0.98;
    public static final double TERMINAL_VY = -3.92;
    static final double SPRINT_JUMP_HORIZONTAL = 0.2;
    private static final double TPS = ServerFlag.SERVER_TICKS_PER_SECOND;

    /** Most recent jump-key press: facing yaw and whether the player was sprinting. */
    public record JumpInfo(double yaw, boolean sprinting) {}

    private final Entity entity;

    private VelocityContext(Entity entity) { this.entity = entity; }

    public static VelocityContext of(Entity entity) { return new VelocityContext(entity); }

    public Entity entity() { return entity; }

    /** Position-delta tracked velocity (blocks/tick); players via the tracker, others via entity velocity. */
    public Vec tracked() { return MovementTracker.tracked(entity); }

    /** Raw server-side entity velocity converted to blocks/tick. */
    public Vec entityVelocity() {
        Vec v = entity.getVelocity();
        return new Vec(v.x() / TPS, v.y() / TPS, v.z() / TPS);
    }

    /** Ticks since the entity left the ground (0 on ground). */
    public int ticksInAir() { return GroundTracker.ticksInAir(entity); }

    /** Left the ground rising (jump or knockback boost) and still in that launch arc. */
    public boolean launched() { return GroundTracker.launched(entity); }

    /** Most recent jump-key press within the recency window, or {@code null}. */
    public @Nullable JumpInfo recentJump() { return MovementTracker.recentJump(entity); }

    /** Sprint-jump horizontal impulse for a facing yaw (blocks/tick). */
    public static Vec sprintJumpImpulse(double yaw) {
        double r = Math.toRadians(yaw);
        return new Vec(-Math.sin(r) * SPRINT_JUMP_HORIZONTAL, 0, Math.cos(r) * SPRINT_JUMP_HORIZONTAL);
    }
}
