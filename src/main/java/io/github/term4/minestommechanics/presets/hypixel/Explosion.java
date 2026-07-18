package io.github.term4.minestommechanics.presets.hypixel;

import io.github.term4.minestommechanics.mechanics.attribute.defense.Bypass;
import io.github.term4.minestommechanics.mechanics.explosion.ExplosionConfig;
import io.github.term4.minestommechanics.mechanics.explosion.ExplosionExposure;
import io.github.term4.minestommechanics.mechanics.vanilla18.Vanilla18;

/**
 * Hypixel explosion: the 1.8 baseline ({@link io.github.term4.minestommechanics.mechanics.vanilla18.Explosion}) plus a
 * constant radial base toward {@code feet+1} (magnitude 0.8, isotropic up/sideways, 0.4× downward). Damage is a flat 2.0
 * (measured: fireball + TNT both deal 2.0 regardless of distance) that ignores armor POINTS but still respects
 * enchants/effects - not the 1.8 falloff curve. Constants fitted on 240+ KB captures.
 */
public final class Explosion {

    private Explosion() {}

    // radial base toward feet+1, magnitude 0.8: isotropic up/sideways (capture-fit MMC-vs-Hypixel), 0.4× (0.32) downward
    private static final double BASE = 0.8;
    private static final double BASE_DOWNWARD_SCALE = 0.4;
    private static final double BASE_HEIGHT = 1.0;
    /** Weak blasts (impact below this) deal no explosion KB - only the projectile KB lands. */
    private static final double KB_IMPACT_FLOOR = 0.435;
    /** Flat damage to every in-range target (fireball + TNT alike), ignoring armor points. */
    private static final double FLAT_DAMAGE = 2.0;

    public static ExplosionConfig config() {
        return ExplosionConfig.builder(Vanilla18.explosion())
                .baseKnockback(BASE).baseDownwardScale(BASE_DOWNWARD_SCALE).baseHeight(BASE_HEIGHT)
                .knockbackImpactFloor(KB_IMPACT_FLOOR)
                .flatDamage(FLAT_DAMAGE).damageBypass(Bypass.builder().armor(true).build())
                .exposure(ExplosionExposure.Rays.LEGACY_1_8_FULL_CUBE) // Hypixel gates off-flat blasts (full-cube), unlike singleplayer 1.8
                .build();
    }
}
