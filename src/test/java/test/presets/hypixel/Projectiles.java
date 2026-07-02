package test.presets.hypixel;

import io.github.term4.minestommechanics.mechanics.projectile.ProjectileBehavior;
import io.github.term4.minestommechanics.mechanics.projectile.ProjectileConfig;
import io.github.term4.minestommechanics.mechanics.projectile.entities.FireballEntity;
import io.github.term4.minestommechanics.mechanics.projectile.entities.ManagedProjectile;
import io.github.term4.minestommechanics.mechanics.projectile.types.Fireball;
import io.github.term4.minestommechanics.mechanics.projectile.types.Pearl;
import io.github.term4.minestommechanics.mechanics.projectile.types.ProjectileTypeConfig;
import net.minestom.server.coordinate.Point;
import net.minestom.server.entity.Entity;
import net.minestom.server.instance.Instance;

/**
 * Hypixel projectiles: the 1.8 throwables/bow ({@link io.github.term4.minestommechanics.mechanics.vanilla18.Projectiles})
 * plus the measured BedWars fireball ({@code bwFireball}: a {@code FIRE_CHARGE} self-propelled, no gravity, detonates on
 * contact at power 2). Only the fireball is BedWars-measured; the rest are inherited. Other modes (skyblock/duels/...) would
 * add their own {@code <mode>Fireball} configs here.
 */
public final class Projectiles {

    private Projectiles() {}

    /** Measured BedWars launch speed (b/t); the 0.1/tick self-propulsion is the entity's. */
    private static final double BW_LAUNCH = 1.0;
    /** Measured BedWars fireball radius (vanilla ghast = 1). */
    private static final double BW_POWER = 2.0;

    /** Hypixel-specific: on impact capture the center and detonate one tick later, so the direct projectile KB lands the tick
     *  before the blast. The lib {@link FireballEntity} detonates same-tick when it has no behavior; this one OWNS the timing. */
    private static final ProjectileBehavior BW_FIREBALL_DETONATION = new ProjectileBehavior() {
        @Override public void onImpact(ManagedProjectile p, Entity hit) {
            if (p instanceof FireballEntity fb) {
                Instance inst = fb.getInstance();
                Point center = fb.getPosition();
                if (inst != null) inst.scheduleNextTick(in -> fb.detonate(in, center, hit));
            }
        }
    };

    public static ProjectileConfig config() {
        ProjectileTypeConfig bwFireball = ProjectileTypeConfig.builder(Fireball.KEY)
                .boundingBox(1, 1, 1) // vanilla EntityFireball.setSize(1,1): the box OTHERS hit to deflect it; its own detonation stays a point (FireballEntity.collisionBox)
                .gravity(0.0).horizontalDrag(0.95).verticalDrag(0.95)
                .speed(BW_LAUNCH)
                .spread(0.0)
                .spawnOffsetForward(0.0).spawnOffsetVertical(0.0).spawnOffsetSideways(0.0) // at the eye
                .leftOwnerImmunity(true)
                .syncInterval(10).velocitySyncInterval(1) // sparse teleports + per-tick velocity: 1.8 extrapolates smoothly between corrections (per-tick teleports snap/jitter on 1.8)
                .removeOnEntityHit(true).removeOnBlockHit(true)
                .damage(0.0)
                .explosionPower(BW_POWER)
                .invulnHit(ProjectileTypeConfig.HitResponse.DESTROY)
                .behavior(BW_FIREBALL_DETONATION) // Hypixel next-tick detonation (lib default is vanilla same-tick)
                .build();
        ProjectileTypeConfig bwPearl = ProjectileTypeConfig.builder(Pearl.KEY)
                .spawnOffsetForward(0.0).spawnOffsetVertical(0.0).spawnOffsetSideways(0.0)
                .boundingBox(0.1, 0.1, 0.1)
                .spread(0.0)
                .build();
        return ProjectileConfig.builder(io.github.term4.minestommechanics.mechanics.vanilla18.Projectiles.config())
                .typeConfigs(bwFireball, bwPearl)
                .build();
    }
}
