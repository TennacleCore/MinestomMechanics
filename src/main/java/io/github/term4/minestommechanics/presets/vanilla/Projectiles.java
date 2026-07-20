package io.github.term4.minestommechanics.presets.vanilla;

import io.github.term4.minestommechanics.mechanics.projectile.ProjectileConfig;
import io.github.term4.minestommechanics.mechanics.projectile.shootables.Bow;
import io.github.term4.minestommechanics.mechanics.projectile.shootables.FishingRod;
import io.github.term4.minestommechanics.mechanics.projectile.types.Arrow;
import io.github.term4.minestommechanics.mechanics.projectile.types.Egg;
import io.github.term4.minestommechanics.mechanics.projectile.types.FishingBobber;
import io.github.term4.minestommechanics.mechanics.projectile.types.Pearl;
import io.github.term4.minestommechanics.mechanics.projectile.types.ProjectileTypeConfig;
import io.github.term4.minestommechanics.mechanics.projectile.types.ProjectileTypeConfig.HitResponse;
import io.github.term4.minestommechanics.mechanics.projectile.types.ProjectileTypeConfig.PhysicsOrder;
import io.github.term4.minestommechanics.mechanics.projectile.types.ProjectileTypeConfig.RodDurability;
import io.github.term4.minestommechanics.mechanics.projectile.types.ProjectileTypeConfig.RodPull;
import io.github.term4.minestommechanics.mechanics.projectile.types.ProjectileTypeConfig.WireGrid;
import io.github.term4.minestommechanics.mechanics.projectile.types.Snowball;
import io.github.term4.minestommechanics.mechanics.projectile.types.SplashPotion;
import io.github.term4.minestommechanics.presets.vanilla18.Vanilla18;

/** Modern (26.1) projectiles: the 1.8 baseline ({@code vanilla18.Projectiles}) with the 26.1 deltas applied. */
public final class Projectiles {

    private Projectiles() {}

    private static final ProjectileConfig BASE_18 = Vanilla18.projectiles();

    /**
     * Modern (26.1) projectile config: the {@link #defaults()} baseline plus per-type entries. The pearl keeps its
     * self-pass-through; the arrow ({@link #arrow()}), splash potion ({@link #splashPotion()}), and fishing bobber
     * ({@link #fishingBobber()}) override physics. Snowball and egg are pure baseline.
     */
    public static ProjectileConfig config() {
        return ProjectileConfig.builder()
                .defaults(defaults())
                .typeConfigs(
                        ProjectileTypeConfig.builder(Snowball.KEY).build(),
                        ProjectileTypeConfig.builder(Egg.KEY).build(),
                        ProjectileTypeConfig.builder(Pearl.KEY).selfHit(HitResponse.PASS_THROUGH).build(),
                        arrow(),
                        splashPotion(),
                        fishingBobber())
                .shootables(new Bow(), new FishingRod())
                .build();
    }

    /**
     * The 26.1 throwable baseline: the 1.8 baseline with the deltas - no throwing-hand lateral, full shooter-momentum
     * inheritance (vertical only when airborne), reverse-damp deflection ({@code motion *= -0.5} + a cosmetic +-10-degree
     * yaw), gravity + drag before the move, shooter immunity until the projectile clears the shooter's box, spawn state
     * on the modern wire. Else matches 1.8.
     */
    public static ProjectileTypeConfig defaults() {
        return modernDeltas(ProjectileTypeConfig.builder(BASE_18.defaults()))
                .gravity(0.03) // 26.1 getDefaultGravity is a plain double (the 1.8 0.03F widens differently)
                .build();
    }

    /**
     * Vanilla 26.1 arrow: the 1.8 arrow (speed 3.0, gravity 0.05, velocity-based damage, sticks in blocks) with the same
     * 26.1 deltas as {@link #defaults()}, plus {@code invulnHit(DEFLECT)} - 26.1 deflects a rejected hit for both the
     * creative-target and invul-window cases (no creative pass-through, unlike 1.8).
     */
    public static ProjectileTypeConfig arrow() {
        return modernDeltas(ProjectileTypeConfig.builder(BASE_18.typeConfig(Arrow.KEY)))
                .gravity(0.05)
                .physicsOrder(PhysicsOrder.DRAG_AFTER_MOVE) // 26.1 arrows kept the 1.8 order; only throwables flipped
                .invulnHit(HitResponse.DEFLECT)
                .build();
    }

    /** Vanilla 26.1 splash potion: the 1.8 splash (speed 0.5, pitch -20, vanilla tracker wire) with the 26.1 deltas
     *  plus the 26.1 impact model and modern particle palette. */
    public static ProjectileTypeConfig splashPotion() {
        return modernDeltas(ProjectileTypeConfig.builder(BASE_18.typeConfig(SplashPotion.KEY)))
                .gravity(0.05)
                .legacyPotionColors(false)
                .modernSplash(true)
                .build();
    }

    /**
     * Vanilla 26.1 fishing bobber: the 1.8 bobber with the 26.1 deltas plus its own launch shape ({@code FishingHook}
     * ctor: 0.3 forward yaw-only at eye height, speed {@code 0.6 + 0.5/cos(pitch)} along the aim - tan clamped ±5 -
     * no shooter momentum), plain-double 0.03/0.92 physics ({@code DRAG_BEFORE_MOVE} = gravity-before-move), and the
     * 26.1 retrieve (no sqrt Y boost, durability 5/2). Spread approximates 26.1's per-axis triangle(0.0103365).
     */
    public static ProjectileTypeConfig fishingBobber() {
        return modernDeltas(ProjectileTypeConfig.builder(BASE_18.typeConfig(FishingBobber.KEY)))
                .gravity(0.03).horizontalDrag(0.92).verticalDrag(0.92)
                .momentum(0.0, 0.0)
                .spawnOffsetVertical(0.0)
                // forward is scaled by the 3D aim; /cos(pitch) restores the yaw-only 0.3 (floored near straight up/down)
                .spawnOffsetForward(ctx -> 0.3 / Math.max(0.1, Math.cos(Math.toRadians(ctx.shooter().getPosition().pitch()))))
                .speed(ctx -> {
                    double tan = Math.tan(Math.toRadians(ctx.shooter().getPosition().pitch()));
                    return 0.6 + 0.5 * Math.sqrt(1 + Math.min(tan * tan, 25.0));
                })
                .spread(0.0103365)
                .rodPull(RodPull.MODERN)
                .rodDurability(RodDurability.MODERN)
                .hookedMetadata(true) // native 26.1: the glued-bobber hooked visual
                .build();
    }

    /** Applies the shared 1.8 -&gt; 26.1 projectile deltas onto {@code b} (over a 1.8 base). */
    private static ProjectileTypeConfig.Builder modernDeltas(ProjectileTypeConfig.Builder b) {
        return b
                .waterModel(ProjectileTypeConfig.WaterModel.MODERN) // fluid-height sensing: no 1.8 inset flicker
                .spawnOffsetSideways(0.0)
                .momentumHorizontal(1.0)
                // 26.1 momentum = the shooter's client motion, read at launch
                .momentumVertical(ctx -> ctx.shooter().isOnGround() ? 0.0 : 1.0) // 26.1 folds vertical only when airborne
                .deflect(-0.5, 0, -10, 10)
                .physicsOrder(PhysicsOrder.DRAG_BEFORE_MOVE)
                .wireGrid(WireGrid.MODERN)
                .leftOwnerImmunity(true);
    }
}
