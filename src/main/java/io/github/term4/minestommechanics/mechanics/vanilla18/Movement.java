package io.github.term4.minestommechanics.mechanics.vanilla18;

import io.github.term4.minestommechanics.tracking.motion.VelocityRule;

/**
 * Vanilla 1.8 movement: the velocity tracking method. The canonical 1.8 value, consumed by the {@link Vanilla18} preset
 * profile (and overridable per scope via {@code MechanicsProfile.velocity}). The player platform config is {@link Player}.
 */
public final class Movement {

    private Movement() {}

    /**
     * Vanilla 1.8 velocity tracking method (the {@code motX/motY/motZ} reconstruction the friction fold and hurt
     * broadcast read). Set on a {@code MechanicsProfile.velocity(...)} scope rather than per config.
     *
     * <p>The attacker self-slowdown (horizontal {@code *= 0.6} on a landed sprint/enchant hit) is implemented on
     * {@code AttackConfig.fullHitScale} - velocity-only, gated separately from victim knockback. Vanilla {@code 0.6};
     * Mmc18 {@code 1.0} (no slowdown).
     */
    public static VelocityRule velocity() {
        return VelocityRule.simulated();
    }
}
