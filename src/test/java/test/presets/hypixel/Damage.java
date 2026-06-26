package test.presets.hypixel;

import io.github.term4.minestommechanics.mechanics.damage.DamageConfig;

/** Hypixel damage: the vanilla 1.8 base with silent overdamage and a 9-tick invul window. */
public final class Damage {

    private Damage() {}

    /** DamageConfig based on Hypixel ({@code hurtKnockback} inherits Vanilla18's zero-impulse config). */
    public static DamageConfig config() {
        return DamageConfig.builder(io.github.term4.minestommechanics.mechanics.vanilla18.Damage.config())
                .overdamageSilent(true)
                .invulTicks(9)
                .build();
    }
}
