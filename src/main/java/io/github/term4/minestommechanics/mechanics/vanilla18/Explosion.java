package io.github.term4.minestommechanics.mechanics.vanilla18;

import io.github.term4.minestommechanics.mechanics.explosion.ExplosionConfig;

/**
 * Vanilla 1.8 explosion config: the damage constant is 8.0 and the per-entity damage is floored (both differ from
 * modern's 7.0 / unfloored). Consumed by the {@link Vanilla18} preset and as the {@code ExplosionSystem} fallback.
 */
public final class Explosion {

    private Explosion() {}

    /** ExplosionConfig with vanilla 1.8 values (TNT-default radius 4.0). */
    public static ExplosionConfig config() {
        return ExplosionConfig.builder()
                .power(4.0)
                .damageConstant(8.0)
                .floorDamage(true)
                .knockbackMultiplier(1.0)
                .exposure(true)
                .build();
    }
}
