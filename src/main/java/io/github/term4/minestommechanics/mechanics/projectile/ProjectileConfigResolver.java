package io.github.term4.minestommechanics.mechanics.projectile;

import io.github.term4.minestommechanics.Services;
import io.github.term4.minestommechanics.config.FieldValue;
import io.github.term4.minestommechanics.tracking.VelocityRule;
import io.github.term4.minestommechanics.mechanics.damage.types.DamageType;
import io.github.term4.minestommechanics.mechanics.knockback.KnockbackConfig;
import io.github.term4.minestommechanics.mechanics.projectile.types.ProjectileTypeConfig;
import net.minestom.server.collision.BoundingBox;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Entity;
import net.minestom.server.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Resolves a {@link ProjectileConfig} + snapshot into the plain values a projectile needs, in TWO phases against the
 * same {@link ProjectileContext}:
 * <ul>
 *   <li>{@link #resolveFlight} at LAUNCH - the spawn + physics knobs (bbox, aerodynamics, spawn offsets, speed,
 *       spread, momentum, shooter immunity, sync). The target is unknown here.</li>
 *   <li>{@link #resolveHit} at IMPACT - the hit knobs (does it hit, damage, knockback, removal), resolved against a
 *       context that carries the struck {@link ProjectileContext#target()} and {@link ProjectileContext#throwOrigin()},
 *       so a config lambda can branch on {@link ProjectileContext#isSelfHit()} or read the throw-time snapshot. Hits
 *       are rare (not per-tick), so resolving them late is cheap and keeps self-hit / throw-time behavior in plain
 *       config instead of the event API.</li>
 * </ul>
 * The merged {@link ProjectileTypeConfig} ({@link ProjectileContext#typeConfig()}) is computed once at launch and
 * reused for the impact resolution.
 */
public final class ProjectileConfigResolver {

    private ProjectileConfigResolver() {}

    /**
     * The context the per-type projectile {@code FieldValue}s resolve against. Launch-time use carries just the
     * snapshot + services; {@link #atHit} adds the impact fields ({@link #target}, {@link #throwOrigin},
     * {@link #hitPos}) for resolving the hit knobs.
     */
    public record ProjectileContext(ProjectileSnapshot snap, @Nullable Services services,
                                    @Nullable Entity target, @Nullable Pos throwOrigin, @Nullable Point hitPos) {
        public static ProjectileContext of(ProjectileSnapshot snap, @Nullable Services services) {
            return new ProjectileContext(snap, services, null, null, null);
        }

        /** Derives the impact-time context (target / throwOrigin / hitPos set) for resolving the hit knobs. */
        public ProjectileContext atHit(@Nullable Entity target, @Nullable Pos throwOrigin, @Nullable Point hitPos) {
            return new ProjectileContext(snap, services, target, throwOrigin, hitPos);
        }

        public Entity shooter() { return snap.shooter(); }
        public @Nullable ItemStack item() { return snap.item(); }
        public double power() { return snap.power(); }

        /** The struck entity (impact only), or {@code null} for a block hit / at launch. */
        public @Nullable Entity target() { return target; }
        /** Whether the struck entity is the shooter itself - the native self-vs-other test for a hit lambda. */
        public boolean isSelfHit() { return target != null && target == snap.shooter(); }
        /** The shooter's position + view at THROW time (impact only); see {@code ProjectileEntity.getShooterOriginPos}. */
        public @Nullable Pos throwOrigin() { return throwOrigin; }
        /** The impact position (impact only). */
        public @Nullable Point hitPos() { return hitPos; }

        /**
         * Effective per-type config for this launch, layered highest-first: the active {@link ProjectileConfig}'s
         * per-type override (snapshot config, else the install config) -&gt; that config's generic
         * {@link ProjectileConfig#defaults() defaults} -&gt; the type's intrinsic
         * {@link io.github.term4.minestommechanics.mechanics.projectile.types.ProjectileType#defaultConfig()},
         * with any context-aware {@code subConfig} overlay layered on top.
         */
        public ProjectileTypeConfig typeConfig() {
            ProjectileConfig cfg = snap.config();
            if (cfg == null && services != null && services.projectiles() != null) cfg = services.projectiles().config();
            ProjectileTypeConfig tc = cfg != null ? cfg.typeConfig(snap.type().key()) : null;
            ProjectileTypeConfig generic = cfg != null ? cfg.defaults() : null;
            ProjectileTypeConfig base = snap.type().defaultConfig();
            if (generic != null) base = generic.fromBase(base);
            if (tc != null) base = tc.fromBase(base);
            if (base.subConfig != null) {
                ProjectileTypeConfig overlay = base.subConfig.apply(this);
                if (overlay != null) base = overlay.fromBase(base);
            }
            return base;
        }
    }

    /** Resolves the FLIGHT knobs (spawn + physics) at launch from the effective type config. */
    public static ResolvedFlight resolveFlight(ProjectileTypeConfig tc, ProjectileContext ctx) {
        return new ResolvedFlight(
                or(resolve(tc.enabled, ctx), Boolean.TRUE),
                or(resolve(tc.boundingBox, ctx), POINT_BOX),
                or(resolve(tc.gravity, ctx), 0.03),
                or(resolve(tc.horizontalDrag, ctx), 0.99),
                or(resolve(tc.verticalDrag, ctx), 0.99),
                or(resolve(tc.spawnOffsetForward, ctx), 0.0),
                or(resolve(tc.spawnOffsetVertical, ctx), 0.0),
                or(resolve(tc.spawnOffsetSideways, ctx), 0.0),
                or(resolve(tc.speed, ctx), 1.5),
                or(resolve(tc.spread, ctx), 0.0),
                or(resolve(tc.momentumHorizontal, ctx), 0.0), // vanilla 1.8 adds NO shooter momentum (26.1 = 1.0)
                or(resolve(tc.momentumVertical, ctx), 0.0),
                resolve(tc.velocity, ctx), // momentum velocity source (config rule); profile/DEFAULT fallback in launchVelocity
                or(resolve(tc.shooterImmunityTicks, ctx), 5),
                or(resolve(tc.entityHitGrow, ctx), 0.3), // vanilla 1.8 Entity{Arrow,Projectile}: target grow 0.3 each side
                or(resolve(tc.syncInterval, ctx), 20),
                or(resolve(tc.velocitySyncInterval, ctx), 0), // 0 = no per-tick velocity (vanilla arrow); the edge-slide fix
                or(resolve(tc.physicsOrder, ctx), ProjectileTypeConfig.PhysicsOrder.DRAG_AFTER_MOVE), // 26.1 = DRAG_BEFORE_MOVE
                or(resolve(tc.leftOwnerImmunity, ctx), Boolean.FALSE), // 26.1 = true (immune until it leaves the shooter box)
                or(resolve(tc.stickPullback, ctx), 0.05), // vanilla 0.05 tip poke-out
                or(resolve(tc.shakeTicks, ctx), 7), // vanilla arrow shake / pickup delay
                or(resolve(tc.behavior, ctx), ProjectileBehavior.NONE),
                resolve(tc.pickupBox, ctx)); // nullable: the entity keeps its vanilla default if unset
    }

    /** Resolves the HIT knobs at impact from the effective type config against the impact {@code ctx}. */
    public static ResolvedHit resolveHit(ProjectileTypeConfig tc, ProjectileContext ctx) {
        return new ResolvedHit(
                or(resolve(tc.selfHit, ctx), ProjectileTypeConfig.HitResponse.HIT),
                resolve(tc.knockback, ctx),
                or(resolve(tc.knockbackSource, ctx), ProjectileTypeConfig.KnockbackSource.PROJECTILE),
                or(resolve(tc.damage, ctx), 0.0),
                resolve(tc.damageType, ctx),
                or(resolve(tc.removeOnEntityHit, ctx), Boolean.TRUE),
                or(resolve(tc.removeOnBlockHit, ctx), Boolean.TRUE),
                or(resolve(tc.invulnHit, ctx), ProjectileTypeConfig.HitResponse.DESTROY), // throwables break on an invuln hit; arrow = DEFLECT
                or(resolve(tc.deflect, ctx), ProjectileTypeConfig.Deflect.of(-0.1)), // vanilla 1.8 motion *= -0.1; 26.1 = deflect(-0.5, 0, -10, 10)
                or(resolve(tc.deflectParticles, ctx), Boolean.FALSE)); // cosmetic deflect-visibility opt-in (default vanilla = off)
    }

    /** Zero-size box (MinestomPVP's POINT_BOX): collision points resolve exactly on block boundaries (fix ledger F1). */
    private static final BoundingBox POINT_BOX = new BoundingBox(0, 0, 0);

    private static <T> T or(@Nullable T v, T def) { return v != null ? v : def; }

    private static <T> @Nullable T resolve(@Nullable FieldValue<ProjectileContext, T> fv, ProjectileContext ctx) {
        return fv != null ? fv.resolve(ctx) : null;
    }

    /** Flight values resolved at launch (spawn + physics). */
    public record ResolvedFlight(
            boolean enabled,
            BoundingBox boundingBox,
            double gravity,
            double horizontalDrag,
            double verticalDrag,
            double spawnOffsetForward,
            double spawnOffsetVertical,
            double spawnOffsetSideways,
            double speed,
            double spread,
            double momentumHorizontal,
            double momentumVertical,
            @Nullable VelocityRule velocity,
            int shooterImmunityTicks,
            double entityHitGrow,
            int syncInterval,
            int velocitySyncInterval,
            ProjectileTypeConfig.PhysicsOrder physicsOrder,
            boolean leftOwnerImmunity,
            double stickPullback,
            int shakeTicks,
            ProjectileBehavior behavior,
            @Nullable ProjectileTypeConfig.PickupBox pickupBox
    ) {}

    /** Hit values resolved at impact ({@code knockback}/{@code damageType} nullable). */
    public record ResolvedHit(
            ProjectileTypeConfig.HitResponse selfHit,
            @Nullable KnockbackConfig knockback,
            ProjectileTypeConfig.KnockbackSource knockbackSource,
            double damage,
            @Nullable DamageType damageType,
            boolean removeOnEntityHit,
            boolean removeOnBlockHit,
            ProjectileTypeConfig.HitResponse invulnHit,
            ProjectileTypeConfig.Deflect deflect,
            boolean deflectParticles
    ) {}
}
