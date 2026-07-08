package test.presets.hypixel;

import io.github.term4.minestommechanics.mechanics.attribute.defense.Bypass;
import io.github.term4.minestommechanics.mechanics.explosion.ExplosionConfig;
import io.github.term4.minestommechanics.mechanics.vanilla18.Vanilla18;

/**
 * Hypixel explosion: the 1.8 baseline ({@link io.github.term4.minestommechanics.mechanics.vanilla18.Explosion}) plus a
 * constant radial base toward {@code feet+1}, anisotropic. Damage is a flat 2.0 (measured: fireball + TNT both deal 2.0
 * regardless of distance) that ignores armor POINTS but still respects enchants/effects - not the 1.8 falloff curve.
 * Constants fitted on 240+ KB captures.
 */
public final class Explosion {

    private Explosion() {}

    // radial base 0.8 toward feet+1: 0.8 up, ×0.8 sideways (0.64), ×0.4 down (0.32)
    private static final double BASE = 0.8;
    private static final double BASE_HORIZONTAL_SCALE = 0.8;
    private static final double BASE_DOWNWARD_SCALE = 0.4;
    private static final double BASE_HEIGHT = 1.0;
    /** Weak blasts (impact below this) deal no explosion KB - only the projectile KB lands. */
    private static final double KB_IMPACT_FLOOR = 0.435;
    /** Flat damage to every in-range target (fireball + TNT alike), ignoring armor points. */
    private static final double FLAT_DAMAGE = 2.0;

    public static ExplosionConfig config() {
        return ExplosionConfig.builder(Vanilla18.explosion())
                .baseKnockback(BASE).baseHorizontalScale(BASE_HORIZONTAL_SCALE).baseDownwardScale(BASE_DOWNWARD_SCALE).baseHeight(BASE_HEIGHT)
                .knockbackImpactFloor(KB_IMPACT_FLOOR)
                .flatDamage(FLAT_DAMAGE).damageBypass(Bypass.builder().armor(true).build())
                .build();
    }
}
