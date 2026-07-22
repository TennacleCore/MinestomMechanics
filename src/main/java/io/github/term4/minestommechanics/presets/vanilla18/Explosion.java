package io.github.term4.minestommechanics.presets.vanilla18;

import io.github.term4.minestommechanics.mechanics.explosion.ExplosionConfig;
import io.github.term4.minestommechanics.mechanics.explosion.ExplosionExposure;

/** Vanilla 1.8 explosion config (damage constant 8.0, floored; modern is 7.0 unfloored); also the {@code ExplosionSystem} fallback. */
public final class Explosion {

    private Explosion() {}

    public static ExplosionConfig config() {
        return ExplosionConfig.builder()
                .power(4.0) // TNT default radius
                .damageConstant(8.0)
                .floorDamage(true)
                .knockbackMultiplier(1.0)
                .exposure(ExplosionExposure.Rays.LEGACY_1_8) // faithful 1.8 rayTraceBlocks (block-real shape, pushes off-flat); servers override to LEGACY_1_8_FULL_CUBE
                .build();
    }
}
