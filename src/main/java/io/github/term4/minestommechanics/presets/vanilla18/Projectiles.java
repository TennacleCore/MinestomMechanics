package io.github.term4.minestommechanics.presets.vanilla18;

import io.github.term4.minestommechanics.mechanics.damage.types.projectile.ProjectileDamage;
import io.github.term4.minestommechanics.mechanics.projectile.ProjectileConfig;
import io.github.term4.minestommechanics.mechanics.projectile.shootables.Bow;
import io.github.term4.minestommechanics.mechanics.projectile.shootables.FishingRod;
import io.github.term4.minestommechanics.mechanics.projectile.types.Arrow;
import io.github.term4.minestommechanics.mechanics.projectile.types.Egg;
import io.github.term4.minestommechanics.mechanics.projectile.types.FishingBobber;
import io.github.term4.minestommechanics.mechanics.projectile.types.Pearl;
import io.github.term4.minestommechanics.mechanics.projectile.types.ProjectileTypeConfig;
import io.github.term4.minestommechanics.mechanics.projectile.types.Snowball;
import io.github.term4.minestommechanics.mechanics.projectile.types.SplashPotion;

/**
 * Vanilla 1.8 projectile config: the generic {@link #defaults()} baseline plus per-type entries. The canonical 1.8
 * values, consumed both by the {@link Vanilla18} preset profile and by {@code ProjectileTypeConfig}/the flight entities.
 */
public final class Projectiles {

    private Projectiles() {}

    // 1.8 constants are FLOATS widened to double each tick; plain 0.99/0.05 drift off the client's prediction
    private static final double DRAG_099F = 0.99f;
    private static final double DRAG_092F = 0.92f;
    private static final double GRAVITY_003F = 0.03f;
    private static final double GRAVITY_004F = 0.04f;
    private static final double GRAVITY_005F = 0.05f;

    /** Vanilla 1.8 projectiles: the {@link #defaults()} baseline + per-type entries (presence enables a type). */
    public static ProjectileConfig config() {
        return ProjectileConfig.builder()
                .defaults(defaults())
                .typeConfigs(
                        ProjectileTypeConfig.builder(Snowball.KEY).build(),
                        ProjectileTypeConfig.builder(Egg.KEY).build(),
                        pearl(),
                        arrow(),
                        splashPotion(),
                        fishingBobber())
                .shootables(new Bow(), new FishingRod())
                .build();
    }

    /** The generic vanilla 1.8 throwable baseline every type inherits unless it overrides a knob (values + rationale inline). Re-base per-type via {@code ProjectileTypeConfig.builder(Projectiles.defaults())...}. */
    public static ProjectileTypeConfig defaults() {
        return ProjectileTypeConfig.builder()
                .boundingBox(0, 0, 0)
                .gravity(GRAVITY_003F).horizontalDrag(DRAG_099F).verticalDrag(DRAG_099F)
                .speed(1.5).spread(0.0075) // momentumHorizontal/Vertical default 0 (1.8 folds no shooter momentum)
                .spawnOffsetVertical(-0.1).spawnOffsetSideways(0.16)
                .shooterImmunityTicks(5)
                .entityHitGrow(0.3)
                // 1.8 EntityTracker throwable row (64, 10, true): position + velocity every 10 ticks
                .syncInterval(10).velocitySyncInterval(10)
                .knockback(Knockback.projectile())
                .knockbackSource(ProjectileTypeConfig.KnockbackSource.SHOOTER)
                .damage(0.0).damageType(ProjectileDamage.INSTANCE)
                .removeOnEntityHit(true).removeOnBlockHit(true)
                .invulnHit(ProjectileTypeConfig.HitResponse.DESTROY)
                .build();
    }

    /**
     * Vanilla 1.8 ender pearl overrides (on {@link #defaults()}): {@code selfHit(PASS_THROUGH)} - the 1.8 pearl ignores
     * its own thrower and passes through (unlike snowball/egg, which can self-hit after the immunity window). The teleport
     * + 5 fall damage live in {@code PearlEntity}.
     */
    public static ProjectileTypeConfig pearl() {
        return ProjectileTypeConfig.builder(Pearl.KEY)
                .selfHit(ProjectileTypeConfig.HitResponse.PASS_THROUGH)
                .build();
    }

    /**
     * Vanilla 1.8 splash potion overrides (on {@link #defaults()}): lobbed slow + high (speed {@code 0.5}, pitch offset
     * {@code -20}, gravity {@code 0.05}) and never a contact hit ({@code entityHit/selfHit DESTROY} - the impact splash in
     * {@code SplashPotionEntity} is the whole effect, no hurt animation / knockback / invul).
     *
     * <p>Wire shape = the 1.8 tracker (updateFrequency 10, capture-verified 2026-07-05): spawn + velocity, then a
     * position + velocity correction every 10 ticks, the client predicting between; the server sim stays full
     * precision. The silent bit-exact client-prediction model ({@code velocitySyncInterval(0)}) is the mmc18 splash.
     */
    public static ProjectileTypeConfig splashPotion() {
        return ProjectileTypeConfig.builder(SplashPotion.KEY)
                .gravity(GRAVITY_005F).speed(0.5).launchPitchOffset(-20.0)
                .legacyPotionColors(true)
                .entityHit(ProjectileTypeConfig.HitResponse.DESTROY)
                .selfHit(ProjectileTypeConfig.HitResponse.DESTROY)
                .build();
    }

    /**
     * Vanilla 1.8 fishing bobber (on {@link #defaults()}): the launch IS the throwable baseline
     * ({@code EntityFishingHook.c()} matches {@code EntityThrowable} exactly); gravity 0.04F before drag 0.92F
     * ({@code FishingBobberEntity} owns the order + water); wire = the real 1.8 tracker (updateFrequency 5, velocity
     * updates on) with the spawn in lockstep so corrections are no-ops. A hit is the 0-damage hook (hurt flash +
     * baseline away-from-angler KB); rejected hits fly on. Pull/durability/32-block line snap = the entity defaults.
     */
    public static ProjectileTypeConfig fishingBobber() {
        return ProjectileTypeConfig.builder(FishingBobber.KEY)
                .gravity(GRAVITY_004F).horizontalDrag(DRAG_092F).verticalDrag(DRAG_092F)
                .syncInterval(5).velocitySyncInterval(5)
                .wireLockstep(true)
                .hookedMetadata(false) // 1.8 has no hooked metadata; modern viewers see the pin position, like real 1.8-through-Via
                .removeOnEntityHit(false).removeOnBlockHit(false)
                .invulnHit(ProjectileTypeConfig.HitResponse.PASS_THROUGH)
                .build();
    }

    /**
     * Vanilla 1.8 arrow overrides (on {@link #defaults()}): faster + heavier (speed {@code 3.0}, gravity {@code 0.05}),
     * velocity-based damage ({@code damage = 2.0} per-speed multiplier), sticks in blocks ({@code removeOnBlockHit = false}).
     * Knockback stays shooter-relative (inherited, not {@code PROJECTILE}): a plain arrow knocks the victim away from the
     * shooter, not along flight; Punch rides as the extra-knockback level in that same direction. Damage routes through {@link ProjectileDamage} (a dedicated {@code minecraft:arrow} type is the follow-up).
     */
    public static ProjectileTypeConfig arrow() {
        return ProjectileTypeConfig.builder(Arrow.KEY)
                .gravity(GRAVITY_005F).speed(3.0)
                // 1.8 tracker arrow row (64, 20, false): no velocity updates, the client predicts from spawn
                .syncInterval(20).velocitySyncInterval(0)
                .damage(2.0).damageType(ProjectileDamage.INSTANCE)
                .removeOnEntityHit(true).removeOnBlockHit(false)
                .invulnHit(ProjectileTypeConfig.HitResponse.DEFLECT, ProjectileTypeConfig.HitResponse.PASS_THROUGH)
                .build();
    }
}
