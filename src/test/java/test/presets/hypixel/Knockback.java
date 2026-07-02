package test.presets.hypixel;

import io.github.term4.minestommechanics.mechanics.knockback.KnockbackConfig;

/** Hypixel melee knockback, measured against live servers: the vanilla 1.8 base with one delta. */
public final class Knockback {

    private Knockback() {}

    /** Measured sprint-hit ("extra") vertical, replacing vanilla's 0.1. */
    private static final double EXTRA_VERTICAL = 0.07;

    public static KnockbackConfig melee() {
        return KnockbackConfig.builder(io.github.term4.minestommechanics.mechanics.vanilla18.Knockback.melee())
                .extraVertical(EXTRA_VERTICAL)
                .build();
    }
}
