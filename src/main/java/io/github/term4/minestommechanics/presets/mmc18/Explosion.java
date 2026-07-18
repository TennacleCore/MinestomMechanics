package io.github.term4.minestommechanics.presets.mmc18;

import io.github.term4.minestommechanics.mechanics.explosion.ExplosionConfig;
import io.github.term4.minestommechanics.mechanics.explosion.ExplosionExposure;
import io.github.term4.minestommechanics.mechanics.vanilla18.Vanilla18;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.Player;

/**
 * mmc18 explosion, closed-form from the 2026-07-01 captures (point-blank sweep fits to ≤0.0013 b/t): vanilla-1.8
 * falloff DAMAGE (exact: 9 @2.42m, 3 @3.38m, power 2 - NOT Hypixel's flat 2.0) + the vanilla two-impulse KB with one
 * global scale {@link #KB_SCALE}: the melee hurt-KB ({@link Knockback#explosionHurt()}) folded before the radial push.
 * Velocity-delivered; the explosion packet stays motion-less (captures: all-zero).
 */
public final class Explosion {

    private Explosion() {}

    /** Radial push multiplier, wire-exact from the point-blank sweep (all three capture rows land on MineMen's shorts). Near but NOT exactly melee B/0.4 = 1.3185. */
    static final double KB_SCALE = 1.3167;

    public static ExplosionConfig config() {
        return ExplosionConfig.builder(Vanilla18.explosion())
                .knockbackMultiplier(KB_SCALE)
                .damageKnockback(Knockback.explosionHurt())
                .packetPush(false)
                .pushEye(Explosion::pushEye)
                .exposure(ExplosionExposure.Rays.LEGACY_1_8_FULL_CUBE) // MineMen gates off-flat blasts (full-cube), unlike singleplayer 1.8
                .build();
    }

    /**
     * 1.8 sneak-aware head height (vanilla {@code getHeadHeight}: eye − 0.08 sneaking). The −1e-6 encodes the captured
     * knife-edge: a blast exactly at the sneak eye pushes DOWN (−0.448 shorts −3586), at the standing eye UP (+1.145).
     */
    private static double pushEye(Entity e) {
        double eye = e.getEntityType().registry().eyeHeight();
        return e instanceof Player p && p.isSneaking() ? eye - 0.08 - 1.0e-6 : eye;
    }

    /** Measured FBF blast-damage scale: the vanilla FLOORED falloff × 0.05 (2026-07-01 leather captures, every clean row wire-exact). */
    static final double FBF_DAMAGE_SCALE = 0.05;

    /**
     * Fireball-Fight minigame variant: same KB as {@link #config()}, blast damage = the vanilla floored curve ×
     * {@link #FBF_DAMAGE_SCALE} (p2 point-blank raw 32 → 1.6, p4 raw 58 → 2.9; armor applies normally - all observed
     * drops divisible by leather's 0.72). Direct hits deal the fireball's vanilla 6.0 CONTACT damage
     * ({@code Projectiles}); the splash after it is overdamage-blocked.
     */
    public static ExplosionConfig fireballFight() {
        return config().toBuilder().damageScale(FBF_DAMAGE_SCALE).build();
    }
}
