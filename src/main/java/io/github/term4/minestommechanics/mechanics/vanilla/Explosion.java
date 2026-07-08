package io.github.term4.minestommechanics.mechanics.vanilla;

import io.github.term4.minestommechanics.mechanics.explosion.ExplosionConfig;
import io.github.term4.minestommechanics.mechanics.explosion.ExplosionExposure;

/** Modern (26.1) explosion config: damage constant 7.0, unfloored (vs 1.8's 8.0, floored). */
public final class Explosion {

    private Explosion() {}

    /** ExplosionConfig with modern (26.1) values (TNT-default radius 4.0). */
    public static ExplosionConfig config() {
        return ExplosionConfig.builder()
                .power(4.0)
                .damageConstant(7.0)
                .floorDamage(false)
                .knockbackMultiplier(1.0)
                .exposure(ExplosionExposure.Rays.MODERN)
                .build();
    }
}
