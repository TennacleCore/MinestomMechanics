package test.presets.hypixel;

import io.github.term4.minestommechanics.mechanics.attribute.defense.Bypass;
import io.github.term4.minestommechanics.mechanics.explosion.ExplosionConfig;

/**
 * Hypixel explosion: the 1.8 baseline ({@link io.github.term4.minestommechanics.mechanics.vanilla18.Explosion}) plus a
 * constant radial base toward {@code feet+1.0}, anisotropic 0.8 up / 0.64 sideways / 0.32 down. Damage is a flat 2.0
 * (measured: fireball + TNT both deal 2.0 regardless of distance) that ignores armor POINTS but still respects
 * enchants/effects (flat 2.0 in leather, heavily cut by Protection IV) - not the 1.8 falloff curve.
 */
public final class Explosion {

    private Explosion() {}

    public static ExplosionConfig config() {
        return ExplosionConfig.builder(io.github.term4.minestommechanics.mechanics.vanilla18.Explosion.config())
                .baseKnockback(0.8).baseHorizontalScale(0.8).baseDownwardScale(0.4).baseHeight(1.0)
                .knockbackImpactFloor(0.435) // weak blasts (impact below this) deal no explosion KB - only the projectile KB lands
                .flatDamage(2.0).damageBypass(Bypass.builder().armor(true).build()) // flat 2.0, ignores armor points (Protection/Resistance still apply)
                .build();
    }
}
