package io.github.term4.minestommechanics.mechanics.vanilla;

import io.github.term4.minestommechanics.mechanics.projectile.ProjectileConfig;
import io.github.term4.minestommechanics.mechanics.projectile.shootables.Bow;
import io.github.term4.minestommechanics.mechanics.projectile.types.Egg;
import io.github.term4.minestommechanics.mechanics.projectile.types.Pearl;
import io.github.term4.minestommechanics.mechanics.projectile.types.ProjectileTypeConfig;
import io.github.term4.minestommechanics.mechanics.projectile.types.ProjectileTypeConfig.HitResponse;
import io.github.term4.minestommechanics.mechanics.projectile.types.ProjectileTypeConfig.PhysicsOrder;
import io.github.term4.minestommechanics.mechanics.projectile.types.Snowball;

/** Modern (26.1) projectiles: the 1.8 baseline ({@code vanilla18.Projectiles}) with the 26.1 deltas applied. */
public final class Projectiles {

    private Projectiles() {}

    /**
     * Modern (26.1) projectile config: the {@link #defaults()} baseline plus per-type entries. The pearl keeps its
     * self-pass-through; the arrow overrides physics ({@link #arrow()}). Snowball and egg are pure baseline.
     */
    public static ProjectileConfig config() {
        return ProjectileConfig.builder()
                .defaults(defaults())
                .typeConfigs(
                        ProjectileTypeConfig.builder(Snowball.KEY).build(),
                        ProjectileTypeConfig.builder(Egg.KEY).build(),
                        ProjectileTypeConfig.builder(Pearl.KEY).selfHit(HitResponse.PASS_THROUGH).build(),
                        arrow())
                .shootables(new Bow()) // the bow launcher (item -> arrow)
                .build();
    }

    /**
     * The 26.1 throwable baseline: the 1.8 baseline with the deltas - no throwing-hand lateral, full shooter-momentum
     * inheritance (vertical only when airborne), reverse-damp deflection ({@code motion *= -0.5} + a cosmetic +-10-degree
     * yaw), drag + gravity before the move, shooter immunity until the projectile clears the shooter's box. Else matches 1.8.
     */
    public static ProjectileTypeConfig defaults() {
        return modernDeltas(ProjectileTypeConfig.builder(io.github.term4.minestommechanics.mechanics.vanilla18.Projectiles.defaults())).build();
    }

    /**
     * Vanilla 26.1 arrow: the 1.8 arrow (speed 3.0, gravity 0.05, velocity-based damage, sticks in blocks) with the same
     * 26.1 deltas as {@link #defaults()}, plus {@code invulnHit(DEFLECT)} - 26.1 deflects a rejected hit for both the
     * creative-target and invul-window cases (no creative pass-through, unlike 1.8).
     */
    public static ProjectileTypeConfig arrow() {
        return modernDeltas(ProjectileTypeConfig.builder(io.github.term4.minestommechanics.mechanics.vanilla18.Projectiles.arrow()))
                .invulnHit(HitResponse.DEFLECT)
                .build();
    }

    /** Applies the shared 1.8 -&gt; 26.1 projectile deltas onto {@code b} (over a 1.8 base). */
    private static ProjectileTypeConfig.Builder modernDeltas(ProjectileTypeConfig.Builder b) {
        return b
                .spawnOffsetSideways(0.0)
                .momentumHorizontal(1.0)
                // 26.1 momentum = the shooter's client motion, read at launch
                .momentumVertical(ctx -> ctx.shooter().isOnGround() ? 0.0 : 1.0) // 26.1 folds vertical only when airborne
                .deflect(-0.5, 0, -10, 10)
                .physicsOrder(PhysicsOrder.DRAG_BEFORE_MOVE)
                .leftOwnerImmunity(true);
    }
}
