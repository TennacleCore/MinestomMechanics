package io.github.term4.minestommechanics.presets.vanilla18;

import io.github.term4.minestommechanics.mechanics.explosion.ExplosionConfig;
import io.github.term4.minestommechanics.mechanics.explosion.ExplosionExposure;

/** Vanilla 1.8 explosion config (damageConstant 8.0, floored; vs modern 7.0/unfloored). Consumed by {@link Vanilla18} and as the {@code ExplosionSystem} fallback. */
public final class Explosion {

    private Explosion() {}

    /** power 4.0 = the TNT default radius. */
    public static ExplosionConfig config() {
        return ExplosionConfig.builder()
                .power(4.0)
                .damageConstant(8.0)
                .floorDamage(true)
                .knockbackMultiplier(1.0)
                .exposure(ExplosionExposure.Rays.LEGACY_1_8) // faithful 1.8 rayTraceBlocks (block-real shape, pushes off-flat); servers override to LEGACY_1_8_FULL_CUBE
                .build();
    }
}
