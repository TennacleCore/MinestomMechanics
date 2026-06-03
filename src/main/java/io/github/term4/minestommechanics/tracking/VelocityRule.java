package io.github.term4.minestommechanics.tracking;

import net.minestom.server.coordinate.Vec;

/**
 * Strategy for estimating an entity's velocity (blocks/tick) used by the knockback friction term.
 * Mirrors the rule pattern of {@code AttackEvent.CriticalRule} / {@code DamageEvent.OverdamageRule}:
 * a single-method interface with a {@link #DEFAULT} and static factories for the built-in strategies.
 *
 * <p>{@link #estimate(VelocityContext)} receives a rich {@link VelocityContext}, so a rule supplied
 * from outside the library composes the same public primitives the built-ins use - an open rule, not
 * a fixed menu. Wire one in via {@code KnockbackConfig.velocityMethod(...)} (or the per-axis
 * {@code velocityMode*} setters).
 */
@FunctionalInterface
public interface VelocityRule {

    /** Estimated velocity (blocks/tick) for the knockback friction term. */
    Vec estimate(VelocityContext ctx);

    /** Rule used when a config does not specify one (position-delta, includes knockback). */
    VelocityRule DEFAULT = delta();

    /** Position delta (includes knockback). Former {@code VelocityMethod.DELTA}. */
    static VelocityRule delta() { return VelocityContext::tracked; }

    /** Input-only: 0 or sprint-jump impulse, excludes knockback. Former {@code VelocityMethod.INPUT}. */
    static VelocityRule input() {
        return ctx -> {
            VelocityContext.JumpInfo j = ctx.recentJump();
            if (j == null) return Vec.ZERO;
            Vec h = j.sprinting() ? VelocityContext.sprintJumpImpulse(j.yaw()) : Vec.ZERO;
            return new Vec(h.x(), VelocityContext.JUMP_Y, h.z());
        };
    }

    /** Entity velocity + sprint-jump injection. Former {@code VelocityMethod.LEGACY}. */
    static VelocityRule legacy() {
        return ctx -> {
            Vec v = ctx.entityVelocity();
            VelocityContext.JumpInfo j = ctx.recentJump();
            if (j == null) return v;
            double vx = v.x(), vz = v.z();
            if (j.sprinting()) {
                Vec h = VelocityContext.sprintJumpImpulse(j.yaw());
                vx = h.x();
                vz = h.z();
            }
            return new Vec(vx, v.y() + VelocityContext.JUMP_Y, vz);
        };
    }

    /** Gravity-predicted arc with default vanilla params (0.08, 0.98). Former {@code VelocityMethod.GRAVITY_PREDICTED}. */
    static VelocityRule gravityPredicted() { return gravityPredicted(null, null); }

    /** Gravity-predicted arc with explicit params (folds in the old {@code gravityPredict*} config fields). */
    static VelocityRule gravityPredicted(Double gravityPerTick, Double scale) {
        return ctx -> {
            double g = gravityPerTick != null ? gravityPerTick : VelocityContext.GRAVITY_PER_TICK;
            double s = scale != null ? scale : VelocityContext.GRAVITY_SCALE;
            boolean launched = ctx.launched();
            int ticks = ctx.ticksInAir() - 1;
            // Walk-off arc runs ~2 gravity ticks ahead of the jump-calibrated sampling (vy=0 takeoff
            // tick + ~1-tick isOnGround lag); the launched/jump arc keeps ticks-1.
            if (!launched) ticks += 2;
            if (ticks <= 0) return new Vec(0, -g * s, 0);
            double v0 = launched ? VelocityContext.JUMP_Y : 0.0;
            double scalePow = Math.pow(s, ticks);
            double vy = v0 * scalePow - g * s * (1 - scalePow) / (1 - s);
            vy = Math.max(VelocityContext.TERMINAL_VY, Math.min(VelocityContext.JUMP_Y, vy));
            return new Vec(0, vy, 0);
        };
    }
}
