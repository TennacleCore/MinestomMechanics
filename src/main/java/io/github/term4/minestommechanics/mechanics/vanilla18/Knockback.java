package io.github.term4.minestommechanics.mechanics.vanilla18;

import io.github.term4.minestommechanics.mechanics.knockback.KnockbackConfig;

/**
 * Vanilla 1.8 knockback configs - the canonical 1.8 values, consumed both by the {@link Vanilla18} preset profile and by
 * the systems that fall back to them ({@code KnockbackSystem}, {@code DamageSystem}).
 */
public final class Knockback {

    private Knockback() {}

    /**
     * Melee knockback with every vanilla 1.8 value set except {@code velocity}, which is deliberately unset so the
     * friction fold reads the scoped rule ({@code MechanicsProfile.velocity(...)}). TODO(modern): 1.20+ KB differs -
     * grounded-only vertical fold, vertical add = power (not 0.4), resistance scales instead of gating, 0.003 clamp +
     * apex micro-step.
     */
    public static KnockbackConfig melee() {
        return KnockbackConfig.builder()
                .sprintBuffer(0)
                .horizontal(0.4)
                .vertical(0.4)
                .extraHorizontal(0.5)
                .extraVertical(0.1)
                .verticalBounds(null, 0.4000000059604645)
                .yawWeight(0.0)
                .extraYawWeight(1.0)
                .pitchWeight(0.0)
                .extraPitchWeight(0.0)
                .heightDelta(0.0)
                .extraHeightDelta(0.0)
                .horizontalCombine(KnockbackConfig.DirectionMode.VECTOR_ADDITION)
                .verticalCombine(KnockbackConfig.DirectionMode.SCALAR)
                .frictionH(2.0)
                .frictionV(2.0)
                .frictionModeH(KnockbackConfig.FrictionMode.DIVISOR)
                .frictionModeV(KnockbackConfig.FrictionMode.DIVISOR)
                .quantizeVelocity(true)
                .build();
    }

    /**
     * Projectile knockback: in 1.8 a thrown projectile uses the thrower's {@link #melee() melee knockback}, as a non-melee
     * hit (no sprint bonus). Punch is 0.6/level (EntityArrow), not melee Knockback's 0.5.
     */
    public static KnockbackConfig projectile() {
        return KnockbackConfig.builder(melee())
                .extraHorizontal(0.6)
                .build();
    }

    /**
     * Generic damage-tick "knockback" (fire, cactus, fall, ...): vanilla broadcasts the victim's server-tracked velocity
     * with no impulse, so this config is all zeroes with a 1:1 friction fold - the velocity rule is the broadcast. Bounds
     * are explicitly cleared (the melee {@code 0.4} vertical cap must not clip a jump's {@code 0.42} seed).
     */
    public static KnockbackConfig hurt() {
        return KnockbackConfig.builder()
                .sprintBuffer(0)
                .horizontal(0.0)
                .vertical(0.0)
                .extraHorizontal(0.0)
                .extraVertical(0.0)
                .horizontalBounds(KnockbackConfig.Bounds.of(null, null))
                .verticalBounds(KnockbackConfig.Bounds.of(null, null))
                .extraHorizontalBounds(KnockbackConfig.Bounds.of(null, null))
                .extraVerticalBounds(KnockbackConfig.Bounds.of(null, null))
                .yawWeight(0.0)
                .extraYawWeight(0.0)
                .pitchWeight(0.0)
                .extraPitchWeight(0.0)
                .heightDelta(0.0)
                .extraHeightDelta(0.0)
                .horizontalCombine(KnockbackConfig.DirectionMode.VECTOR_ADDITION)
                .verticalCombine(KnockbackConfig.DirectionMode.SCALAR)
                .frictionH(1.0)
                .frictionV(1.0)
                .frictionModeH(KnockbackConfig.FrictionMode.FACTOR)
                .frictionModeV(KnockbackConfig.FrictionMode.FACTOR)
                // velocity deliberately unset: the broadcast folds the scoped rule (MechanicsProfile.velocity).
                .quantizeVelocity(true)
                .build();
    }
}
