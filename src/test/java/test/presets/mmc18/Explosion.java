package test.presets.mmc18;

import io.github.term4.minestommechanics.mechanics.explosion.ExplosionConfig;
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
        return ExplosionConfig.builder(io.github.term4.minestommechanics.mechanics.vanilla18.Explosion.config())
                .knockbackMultiplier(KB_SCALE)
                .damageKnockback(Knockback.explosionHurt())
                .packetPush(false)
                .pushEye(Explosion::pushEye)
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

    /**
     * Fireball-Fight minigame variant: same KB as {@link #config()} but low FLAT damage instead of the vanilla falloff -
     * in FBF you're knocked off, not blasted to death. {@code flatDamage(6.0)} is a ROUGH GUESS (leather-armor + regen data:
     * clean drops ~4.32 = 6×0.72; power-4 reads were noisier) - recapture no-armor to nail it. Armor still applies (no bypass).
     */
    public static ExplosionConfig fireballFight() {
        return config().toBuilder().flatDamage(2.0).build();
    }
}
