package io.github.term4.minestommechanics.presets.mmc18;

import io.github.term4.minestommechanics.mechanics.explosion.BlockBreaking;
import io.github.term4.minestommechanics.mechanics.explosion.ExplosionConfig;
import io.github.term4.minestommechanics.mechanics.explosion.ExplosionExposure;
import io.github.term4.minestommechanics.mechanics.projectile.entities.FireballEntity;
import io.github.term4.minestommechanics.presets.vanilla18.Vanilla18;
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

    // radial push, wire-exact from the point-blank sweep; near but NOT exactly melee B/0.4 = 1.3185
    static final double KB_SCALE = 1.3167;
    // TNT: flat-pad capture fit (counts + P(r) curves within ~2%): the vanilla ray algorithm at a sparse 8-shell,
    // paying resistance x0.0775 once per block
    static final double CHARGE_SCALE = 0.0775;
    static final int RAY_GRID = 8;
    private static final BlockBreaking TNT_RAYS =
            io.github.term4.minestommechanics.presets.vanilla18.Explosion.blockBreaking().toBuilder()
                    .rayGrid(RAY_GRID)
                    .charge(r -> r * CHARGE_SCALE)
                    .charging(BlockBreaking.Charging.PER_BLOCK)
                    .interaction(BlockBreaking.Interaction.DESTROY_NO_DROPS) // MineMen explosions never drop
                    .build();

    // Fireball: threshold rays on the full 16-shell, one 0.6-1.4x roll per horizontal heading - a block whose gate
    // exceeds the ray's remaining intensity stops it (shields), anything weaker breaks free (only distance decays).
    // The heading-shared roll makes MineMen's rims wiggle coherently (few lone fingers/bites) while staying ragged and
    // unsymmetric, and the vanilla 0.3 sampling corner-cuts like theirs. The gate law anchors all six captured
    // materials within ±0.007 (totals 0.98-1.02); end stone's 2.66 gate exceeds the 2.575 a ray can carry to it
    // (max roll minus one decay step): fireball-proof with no special case.
    private static final BlockBreaking FIREBALL_RAYS =
            io.github.term4.minestommechanics.presets.vanilla18.Explosion.blockBreaking().toBuilder()
                    .charging(BlockBreaking.Charging.THRESHOLD)
                    .intensityRoll(0.6, 1.4)
                    .rollPerHeading(true)
                    .originLift(0.25) // 52-shot wool corpus: footprints shrink faster with standoff than a feet-origin walk
                    .charge(r -> 0.322 + 0.260 * r)
                    .interaction(BlockBreaking.Interaction.DESTROY_NO_DROPS)
                    .build();

    public static ExplosionConfig config() {
        ExplosionConfig base = Vanilla18.explosion();
        return ExplosionConfig.builder(base)
                .knockbackMultiplier(KB_SCALE)
                .blockBreaking(ctx -> ctx.source() instanceof FireballEntity ? FIREBALL_RAYS : TNT_RAYS)
                .damageKnockback(Knockback.explosionHurt())
                .packetPush(false)
                .pushEye(Explosion::pushEye)
                .exposure(ExplosionExposure.Rays.LEGACY_1_8_FULL_CUBE) // MineMen gates off-flat blasts (full-cube), unlike singleplayer 1.8
                .fire(false) // MineMen fireballs never ignite (overrides vanilla18's fireball incendiary); fireballFight() inherits this
                .build();
    }

    // 1.8 getHeadHeight (eye − 0.08 sneaking); the −1e-6 is the captured knife-edge: a blast exactly at the sneak eye
    // pushes DOWN, at the standing eye UP
    private static double pushEye(Entity e) {
        double eye = e.getEntityType().registry().eyeHeight();
        return e instanceof Player p && p.isSneaking() ? eye - 0.08 - 1.0e-6 : eye;
    }

    // measured: the vanilla FLOORED falloff × 0.05 (2026-07-01 leather captures)
    static final double FBF_DAMAGE_SCALE = 0.05;

    /**
     * Fireball-Fight variant: same KB as {@link #config()}, blast damage scaled off the vanilla floored curve; armor
     * applies normally. Direct hits deal the fireball's vanilla 6.0 CONTACT damage, and the splash after it is
     * overdamage-blocked.
     */
    public static ExplosionConfig fireballFight() {
        return config().toBuilder().damageScale(FBF_DAMAGE_SCALE).build();
    }
}
