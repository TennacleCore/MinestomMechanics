package io.github.term4.minestommechanics.presets.hypixel;

import io.github.term4.minestommechanics.mechanics.projectile.ProjectileBehavior;
import io.github.term4.minestommechanics.mechanics.projectile.ProjectileConfig;
import io.github.term4.minestommechanics.mechanics.projectile.entities.FireballEntity;
import io.github.term4.minestommechanics.mechanics.projectile.entities.ManagedProjectile;
import io.github.term4.minestommechanics.mechanics.projectile.types.Fireball;
import io.github.term4.minestommechanics.mechanics.projectile.types.Pearl;
import io.github.term4.minestommechanics.mechanics.projectile.types.ProjectileTypeConfig;
import io.github.term4.minestommechanics.presets.vanilla18.Vanilla18;
import net.minestom.server.ServerFlag;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.instance.Instance;

/**
 * Hypixel projectiles: the 1.8 throwables/bow ({@link io.github.term4.minestommechanics.presets.vanilla18.Projectiles})
 * plus the measured BedWars fireball ({@code bwFireball}: a {@code FIRE_CHARGE}, no gravity, power 2, detonating a POINT
 * on contact). It coasts at {@link #BW_LAUNCH} for {@link #BW_LAUNCH_TICKS} ticks then snaps to {@link #BW_CRUISE} - the
 * one profile that byte-fits BOTH the straight-down 1.645 KB and the ~53&deg; point-blank window.
 */
public final class Projectiles {

    private Projectiles() {}

    // first-tick move: sets the point-blank yaw window (~53 deg) and the close-range block-break offset
    private static final double BW_LAUNCH = 0.5;
    // capture cruise: wire 8359 = 1.0449 (the ramp then rides the (v+0.1)*0.95 propulsion). Only affects flight/far-throw, not the byte-fit KB.
    private static final double BW_CRUISE = 1.0449;
    // byte-forced at 2: a straight-down throw then detonates at feet+0.62 (1.62 - 0.5 - 0.5) = the exact 1.645 KB, while
    // tick 1 stays 0.5 for the window. A 1-tick hold lands feet+0.075 (1.781); the raw propulsion ramp feet+0.55 (1.6625).
    private static final int BW_LAUNCH_TICKS = 2;
    // measured radius; vanilla ghast = 1
    private static final double BW_POWER = 2.0;

    // stateful per launch: FireballEntity's built-in propulsion ramps the move every tick, so re-assert the launch speed
    // through the hold, then snap to cruise. Detonation is a tick late so the direct projectile KB lands before the blast.
    private static ProjectileBehavior bwFireball() {
        return new ProjectileBehavior() {
            private int moves;
            @Override public void onTick(ManagedProjectile p, long time) {
                if (++moves < BW_LAUNCH_TICKS) reaim(p, BW_LAUNCH);
                else if (moves == BW_LAUNCH_TICKS) reaim(p, BW_CRUISE);
            }
            @Override public void onImpact(ManagedProjectile p, Entity hit) {
                if (p instanceof FireballEntity fb) {
                    Instance inst = fb.getInstance();
                    Point center = fb.getPosition();
                    if (inst != null) inst.scheduleNextTick(in -> fb.detonate(in, center, hit));
                }
            }
        };
    }

    // no gravity, so the flight direction is fixed: rescale the current velocity to speedBt (blocks/tick) along it
    private static void reaim(ManagedProjectile p, double speedBt) {
        Vec v = p.getVelocity();
        if (v.lengthSquared() > 1.0e-12) p.setVelocity(v.normalize().mul(speedBt * ServerFlag.SERVER_TICKS_PER_SECOND));
    }

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
                .behavior(ctx -> bwFireball())
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
