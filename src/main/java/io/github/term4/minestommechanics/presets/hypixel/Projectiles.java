package io.github.term4.minestommechanics.presets.hypixel;

import io.github.term4.minestommechanics.mechanics.projectile.ProjectileBehavior;
import io.github.term4.minestommechanics.mechanics.projectile.ProjectileConfig;
import io.github.term4.minestommechanics.mechanics.projectile.entities.FireballEntity;
import io.github.term4.minestommechanics.mechanics.projectile.entities.ManagedProjectile;
import io.github.term4.minestommechanics.mechanics.projectile.types.Fireball;
import io.github.term4.minestommechanics.mechanics.projectile.types.Pearl;
import io.github.term4.minestommechanics.mechanics.projectile.types.ProjectileTypeConfig;
import io.github.term4.minestommechanics.presets.vanilla18.Vanilla18;
import net.minestom.server.coordinate.Point;
import net.minestom.server.entity.Entity;
import net.minestom.server.instance.Instance;

/**
 * Hypixel projectiles: the 1.8 throwables/bow ({@link io.github.term4.minestommechanics.presets.vanilla18.Projectiles})
 * plus the measured BedWars fireball ({@code bwFireball}: a {@code FIRE_CHARGE} self-propelled, no gravity, detonates on
 * contact at power 2). Only the fireball is BedWars-measured; the rest are inherited.
 */
public final class Projectiles {

    private Projectiles() {}

    // measured b/t; the 0.1/tick self-propulsion is the entity's
    private static final double BW_LAUNCH = 1.0;
    // measured radius; vanilla ghast = 1
    private static final double BW_POWER = 2.0;

    // detonate one tick late so the direct projectile KB lands before the blast; overrides FireballEntity's same-tick default
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
                .boundingBox(1, 1, 1) // EntityFireball.setSize(1,1): the box OTHERS hit to deflect it; its own detonation stays a point
                .gravity(0.0).horizontalDrag(0.95).verticalDrag(0.95)
                .speed(BW_LAUNCH)
                .spread(0.0)
                .spawnOffsetForward(0.0).spawnOffsetVertical(0.0).spawnOffsetSideways(0.0) // at the eye
                .leftOwnerImmunity(true)
                .syncInterval(10).velocitySyncInterval(1) // per-tick teleports snap/jitter on 1.8; it extrapolates smoothly between sparse corrections
                .removeOnEntityHit(true).removeOnBlockHit(true)
                .damage(0.0)
                .explosionPower(BW_POWER)
                .invulnHit(ProjectileTypeConfig.HitResponse.DESTROY)
                .behavior(BW_FIREBALL_DETONATION)
                .build();
        ProjectileTypeConfig bwPearl = ProjectileTypeConfig.builder(Pearl.KEY)
                .spawnOffsetForward(0.0).spawnOffsetVertical(0.0).spawnOffsetSideways(0.0)
                .boundingBox(0.1, 0.1, 0.1)
                .spread(0.0)
                .build();
        return ProjectileConfig.builder(Vanilla18.projectiles())
                .typeConfigs(bwFireball, bwPearl)
                .build();
    }
}
