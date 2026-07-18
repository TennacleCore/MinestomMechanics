package io.github.term4.minestommechanics.presets.mmc18;

import io.github.term4.minestommechanics.mechanics.projectile.ProjectileBehavior;
import io.github.term4.minestommechanics.mechanics.projectile.ProjectileConfig;
import io.github.term4.minestommechanics.mechanics.projectile.entities.FireballEntity;
import io.github.term4.minestommechanics.mechanics.projectile.entities.ManagedProjectile;
import io.github.term4.minestommechanics.mechanics.projectile.types.Arrow;
import io.github.term4.minestommechanics.mechanics.projectile.types.Egg;
import io.github.term4.minestommechanics.mechanics.projectile.types.Fireball;
import io.github.term4.minestommechanics.mechanics.projectile.types.FishingBobber;
import io.github.term4.minestommechanics.mechanics.projectile.types.Pearl;
import io.github.term4.minestommechanics.mechanics.projectile.types.ProjectileTypeConfig;
import io.github.term4.minestommechanics.mechanics.projectile.types.Snowball;
import io.github.term4.minestommechanics.mechanics.projectile.types.SplashPotion;
import io.github.term4.minestommechanics.mechanics.vanilla18.Vanilla18;
import net.minestom.server.ServerFlag;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.instance.Instance;

import java.util.concurrent.ThreadLocalRandom;

/**
 * mmc18 projectiles: the 1.8 baseline plus the minemen fireball (FIRE_CHARGE, self-propelled, no gravity, power 2),
 * the silent-wire splash, and the pseudo-hook rod ({@link PseudoHook}).
 * Fireball flight measured from the 2026-07-01 MineMen flight logs (fireball_flight.py): spawn at the shot eye, first tick
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
        ProjectileConfig base = Vanilla18.projectiles();
        ProjectileTypeConfig fireball = ProjectileTypeConfig.builder(Fireball.KEY)
                .boundingBox(1, 1, 1)
                .gravity(0.0).horizontalDrag(0.95).verticalDrag(0.95)
                .speed(LAUNCH).spread(0.0)
                .spawnOffsetForward(0.0).spawnOffsetVertical(0.0).spawnOffsetSideways(0.0)
                .leftOwnerImmunity(true)
                .syncInterval(10).velocitySyncInterval(1)
                .removeOnEntityHit(true).removeOnBlockHit(true)
                .damage(CONTACT_DAMAGE)
                .knockback(Knockback.explosionHurt())
                .knockbackSource(ProjectileTypeConfig.KnockbackSource.PROJECTILE)
                .explosionPower(POWER)
                .invulnHit(ProjectileTypeConfig.HitResponse.DESTROY)
                .behavior(MINEMEN_FLIGHT)
                .build();
        // capture 2026-07-06: 0.55 (not 0.5), no spread, silent flight (spawn + velocity dup only)
        ProjectileTypeConfig splash = ProjectileTypeConfig.builder(base.typeConfig(SplashPotion.KEY))
                .speed(0.55).spread(0.0)
                .syncInterval(0).velocitySyncInterval(0)
                .build();
        // rod: fully client-predicted silent wire (lockstep spawn on the 1.8 grid) + the pseudo-hook behavior.
        // capture 2026-07-06: spread collapsed onto the magnitude - speed 1.5*(1+N(0,0.0075)), direction exact
        ProjectileTypeConfig bobber = ProjectileTypeConfig.builder(base.typeConfig(FishingBobber.KEY))
                .speed(ctx -> 1.5 * (1 + ThreadLocalRandom.current().nextGaussian() * 0.007499999832361937))
                .spread(0.0)
                .syncInterval(0).velocitySyncInterval(0)
                .behavior(ctx -> new PseudoHook())
                .hookHalt(true) // the glued flash needs the same-tick halt + pin on the silent wire
                .selfHit(ProjectileTypeConfig.HitResponse.HIT) // MineMen: you CAN hook yourself (vanilla can't)
                .knockback(Knockback.rod())
                // SHOOTER-relative like vanilla (1.8 EntityLiving.damageEntity reads the indirect source = the angler)
                .knockbackSource(ProjectileTypeConfig.KnockbackSource.SHOOTER)
                .rodPull(new ProjectileTypeConfig.RodPull(0.1, 0.08, false, false))
                .build();
        // capture 2026-07-06: vanilla launch/flight, zero spread + the 0.05 wire vy floor (potions/hook exempt;
        // arrows unmeasured, left vanilla)
        ProjectileTypeConfig snowball = ProjectileTypeConfig.builder(Snowball.KEY)
                .spread(0.0).wireMotYFloor(0.05).knockback(Knockback.projectile()).build();
        ProjectileTypeConfig egg = ProjectileTypeConfig.builder(Egg.KEY)
                .spread(0.0).wireMotYFloor(0.05).knockback(Knockback.projectile()).build();
        ProjectileTypeConfig pearl = ProjectileTypeConfig.builder(base.typeConfig(Pearl.KEY))
                .spread(0.0).wireMotYFloor(0.05).knockback(Knockback.projectile()).build();
        ProjectileTypeConfig arrow = ProjectileTypeConfig.builder(base.typeConfig(Arrow.KEY))
                .knockback(Knockback.arrow()).build();
        return ProjectileConfig.builder(base)
                .typeConfigs(fireball, splash, bobber, snowball, egg, pearl, arrow)
                .shootables(new PseudoHook.Installer()) // the re-flash-on-move listener
                .useItemAimSync(true) // MineMen launches on the CLICK-time aim (in-game: flick-throws never desync)
                .build();
    }
}
