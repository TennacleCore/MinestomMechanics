package test.presets.hypixel;

import io.github.term4.minestommechanics.mechanics.knockback.KnockbackConfig;

/** Hypixel knockback: the vanilla 1.8 melee base with extra vertical (0.07). */
public final class Knockback {

    private Knockback() {}

    public static KnockbackConfig melee() {
        return KnockbackConfig.builder(io.github.term4.minestommechanics.mechanics.vanilla18.Knockback.melee())
                .extraVertical(0.07)
                .build();
    }
}
