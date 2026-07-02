package test.presets.mmc18;

import io.github.term4.minestommechanics.mechanics.projectile.ProjectileBehavior;
import io.github.term4.minestommechanics.mechanics.projectile.ProjectileConfig;
import io.github.term4.minestommechanics.mechanics.projectile.entities.FireballEntity;
import io.github.term4.minestommechanics.mechanics.projectile.entities.ManagedProjectile;
import io.github.term4.minestommechanics.mechanics.projectile.types.Fireball;
import io.github.term4.minestommechanics.mechanics.projectile.types.ProjectileTypeConfig;
import net.minestom.server.ServerFlag;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.instance.Instance;

/**
 * mmc18 projectiles: the 1.8 baseline plus the minemen fireball (FIRE_CHARGE, self-propelled, no gravity, power 2).
 * Flight measured from the 2026-07-01 MineMen flight logs (fireball_flight.py): spawn at the shot eye, first tick
 * moves {@link #LAUNCH}, then the velocity snaps to {@link #CRUISE} and rides the vanilla propulsion curve
 * ((v+0.1)·0.95 → 1.0884…). A direct hit is the vanilla 6.0 CONTACT hit with the mmc18 hurt-KB away from the fireball
 * (FBF captures); the same-tick splash then lands in the contact's i-frame window (FBF's ×0.05 damage = blocked,
 * normal-mode falloff = overdamage remainder + push).
 */
public final class Projectiles {

    private Projectiles() {}

    // measured: wire launch 0.5645 × drag = 0.5363 first-tick move; cruise 1.0457 b/t from tick 2
    private static final double LAUNCH = 0.5645 * 0.95;
    private static final double CRUISE = 1.0457;
    /** Measured MineMen fireball radius (vanilla ghast = 1). */
    private static final double POWER = 2.0;
    /** Direct-hit contact damage (vanilla {@code EntityLargeFireball} 6.0; FBF captures show it unchanged). */
    private static final double CONTACT_DAMAGE = 6.0;

    /**
     * Snaps to cruise speed after the first move (onSpawn fires post-move; a spawn-tick wall detonation never gets
     * here). A behavior owns detonation timing ({@code FireballEntity} skips its bare same-tick detonate), so
     * onImpact detonates same-tick like vanilla/MineMen.
     */
    private static final ProjectileBehavior MINEMEN_FLIGHT = new ProjectileBehavior() {
        @Override public void onSpawn(ManagedProjectile p) {
            Vec v = p.getVelocity();
            if (v.lengthSquared() > 1.0e-12) p.setVelocity(v.normalize().mul(CRUISE * ServerFlag.SERVER_TICKS_PER_SECOND));
        }
        @Override public void onImpact(ManagedProjectile p, Entity hit) {
            Instance instance = p.getInstance();
            if (p instanceof FireballEntity fb && instance != null) fb.detonate(instance, fb.getPosition(), hit);
        }
    };

    public static ProjectileConfig config() {
        ProjectileTypeConfig fireball = ProjectileTypeConfig.builder(Fireball.KEY)
                .boundingBox(1, 1, 1) // the box others hit to deflect it; its own detonation stays a point (FireballEntity.collisionBox)
                .gravity(0.0).horizontalDrag(0.95).verticalDrag(0.95)
                .speed(LAUNCH).spread(0.0)
                .spawnOffsetForward(0.0).spawnOffsetVertical(0.0).spawnOffsetSideways(0.0)
                .leftOwnerImmunity(true)
                .syncInterval(10).velocitySyncInterval(1) // sparse teleport + per-tick velocity: 1.8 extrapolates smoothly (per-tick teleport jitters)
                .removeOnEntityHit(true).removeOnBlockHit(true)
                // vanilla 6.0 contact hit (FBF capture: three exact 6×0.72 leather drops), with the mmc18 hurt-KB away
                // from the fireball; the same-tick splash is then i-framed (FBF: blocked outright, normal: overdamage)
                .damage(CONTACT_DAMAGE)
                .knockback(Knockback.explosionHurt())
                .knockbackSource(ProjectileTypeConfig.KnockbackSource.PROJECTILE)
                .explosionPower(POWER)
                .invulnHit(ProjectileTypeConfig.HitResponse.DESTROY)
                .behavior(MINEMEN_FLIGHT)
                .build();
        return ProjectileConfig.builder(io.github.term4.minestommechanics.mechanics.vanilla18.Projectiles.config())
                .typeConfigs(fireball)
                .build();
    }
}
