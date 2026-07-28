package io.github.term4.polyp.presets.mmc18;

import io.github.term4.polyp.mechanics.projectile.ProjectileBehavior;
import io.github.term4.polyp.mechanics.projectile.ProjectileConfig;
import io.github.term4.polyp.mechanics.projectile.entities.ManagedProjectile;
import io.github.term4.polyp.mechanics.projectile.entities.PearlEntity;
import io.github.term4.polyp.mechanics.projectile.types.Arrow;
import io.github.term4.polyp.mechanics.projectile.types.Egg;
import io.github.term4.polyp.mechanics.projectile.types.Fireball;
import io.github.term4.polyp.mechanics.projectile.types.FishingBobber;
import io.github.term4.polyp.mechanics.projectile.types.Pearl;
import io.github.term4.polyp.mechanics.projectile.types.ProjectileTypeConfig;
import io.github.term4.polyp.mechanics.projectile.types.Snowball;
import io.github.term4.polyp.mechanics.projectile.types.SplashPotion;
import io.github.term4.polyp.presets.vanilla18.Vanilla18;
import io.github.term4.polyp.world.MechanicsWorld;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Entity;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockFace;

/**
 * mmc18 projectiles: the 1.8 baseline plus the minemen fireball (FIRE_CHARGE, self-propelled, no gravity, power 2),
 * the silent-wire splash, and the pseudo-hook rod ({@link PseudoHook}).
 * Fireball flight measured from the 2026-07-01 MineMen flight logs (fireball_flight.py): spawn at the shot eye, first tick
 * moves {@link #LAUNCH}, then the velocity snaps to {@link #CRUISE} and rides the vanilla propulsion curve
 * ((v+0.1)&middot;0.95 -&gt; 1.0884...). A direct hit is the vanilla 6.0 CONTACT hit with the mmc18 hurt-KB away from the fireball;
 * the same-tick splash then lands in the contact's i-frame window (FBF's &times;0.05 damage = blocked, normal-mode falloff =
 * overdamage remainder + push).
 */
public final class Projectiles {

    private Projectiles() {}

    // measured: wire launch 0.5645 x drag = 0.5363 first-tick move; cruise 1.0457 b/t from tick 2
    private static final double LAUNCH = 0.5645 * 0.95;
    private static final double CRUISE = 1.0457;
    // measured radius; vanilla ghast = 1
    private static final double POWER = 2.0;
    // vanilla EntityLargeFireball 6.0, unchanged in the FBF captures
    private static final double CONTACT_DAMAGE = 6.0;
    // every minemen projectile floors |motY| to 0.05 on the wire (sim untouched); vertical-launch types (splash) just hid it
    private static final double WIRE_MOTY_FLOOR = 0.05;

    // pearl teleport (2026-07-27 captures, ~110 tps + the 96-throw geometry corpus + fireball catches): the
    // pearl's CONTINUOUS position, collision axis resolved to face + 0.4 out (floor 0, ceiling -2); a WALL hit
    // also pushes 0.4 back along the free horizontal axis, opposite the travel (floor/ceiling get no horizontal
    // shift); an entity hit lands ONE block off the struck entity, horizontally toward the pearl, at the entity's
    // y (fb-catch wire: tp y == fb y to /32, horizontal fits mean 0.11 - the block-to-the-side self-catch feel;
    // grounded victims still land you on their ground). Look untouched. Teleport damage is per-mode: the
    // bedwars-adjacent modes set teleportDamage(0) on their variant config.
    private static final ProjectileBehavior PEARL_TELEPORT = new ProjectileBehavior() {
        @Override public void onImpact(ManagedProjectile p, Entity hit) {
            if (!(p instanceof PearlEntity pearl)) return;
            Point at = pearl.impactPosition() != null ? pearl.impactPosition() : pearl.getPosition();
            BlockFace face = pearl.impactFace();
            Pos target;
            if (hit != null) {
                Pos e = hit.getPosition();
                double dx = at.x() - e.x(), dz = at.z() - e.z();
                double m = Math.hypot(dx, dz);
                target = m > 1e-6 ? new Pos(e.x() + dx / m, e.y(), e.z() + dz / m) : new Pos(e.x(), e.y(), e.z());
            } else if (face == null) {
                target = new Pos(at.x(), at.y(), at.z());
            } else {
                Point spawn = pearl.getSpawnPosition() != null ? pearl.getSpawnPosition() : at;
                target = switch (face) {
                    case TOP -> new Pos(at.x(), Math.round(at.y()), at.z());
                    case BOTTOM -> new Pos(at.x(), Math.round(at.y()) - 2, at.z());
                    case EAST, WEST -> new Pos(Math.round(at.x()) + face.toDirection().normalX() * 0.4, at.y(),
                            at.z() - 0.4 * Math.signum(at.z() - spawn.z()));
                    case NORTH, SOUTH -> new Pos(at.x() - 0.4 * Math.signum(at.x() - spawn.x()), at.y(),
                            Math.round(at.z()) + face.toDirection().normalZ() * 0.4);
                };
            }
            // refusal (geometry corpus 96/96): the target's BLOCK COLUMN feet..head must be free - not the
            // 0.6-wide box (a wall-hugging target 0.03 from a face was allowed)
            if (columnFree(pearl, target)) pearl.teleportShooter(target);
            else pearl.consumeOnShooter();
        }
    };

    /** The target's block column from feet to head, non-solid throughout (unloaded = blocked). */
    private static boolean columnFree(ManagedProjectile p, Pos at) {
        MechanicsWorld world = MechanicsWorld.of(p);
        int x = (int) Math.floor(at.x()), z = (int) Math.floor(at.z());
        int head = (int) Math.floor(at.y() + 1.79);
        for (int y = (int) Math.floor(at.y()); y <= head; y++) {
            BlockVec pos = new BlockVec(x, y, z);
            if (!world.isChunkLoaded(pos)) return false;
            if (world.getBlock(pos, Block.Getter.Condition.TYPE).isSolid()) return false;
        }
        return true;
    }

    public static ProjectileConfig config() {
        ProjectileConfig base = Vanilla18.projectiles();
        ProjectileTypeConfig fireball = ProjectileTypeConfig.builder(Fireball.KEY)
                .boundingBox(1, 1, 1)
                .gravity(0.0).horizontalDrag(0.95).verticalDrag(0.95)
                .speed(LAUNCH).coastTicks(1).cruiseSpeed(CRUISE).spread(0.0) // coast one tick at launch, then ignite to cruise
                .spawnOffsetForward(0.0).spawnOffsetVertical(0.0).spawnOffsetSideways(0.0)
                .leftOwnerImmunity(true)
                .syncInterval(0).velocitySyncInterval(1) // no position teleports (minemen doesn't): pure velocity prediction
                .removeOnEntityHit(true).removeOnBlockHit(true)
                .selfHit(ProjectileTypeConfig.HitResponse.PASS_THROUGH) // your own fireball never hits you; a deflect reassigns ownership
                .damage(CONTACT_DAMAGE)
                .knockback(Knockback.explosionHurt())
                .knockbackSource(ProjectileTypeConfig.KnockbackSource.PROJECTILE)
                .explosionPower(POWER)
                .invulnHit(ProjectileTypeConfig.HitResponse.DESTROY)
                .build(); // no behavior: the bare fireball detonates same-tick at its pre-move centre
        // capture 2026-07-06: 0.55 (not 0.5), no spread, silent flight (spawn + velocity dup only)
        ProjectileTypeConfig splash = ProjectileTypeConfig.builder(base.typeConfig(SplashPotion.KEY))
                .speed(0.55).spread(0.0)
                .syncInterval(0).velocitySyncInterval(0)
                .build();
        // rod: fully client-predicted silent wire (lockstep spawn on the 1.8 grid).
        // capture 2026-07-28: CONSTANT 1.5*1.0075, direction exact, zero spread (9 identical axis casts, wire
        // 12090 = 1.51125; the 07-06 gaussian read was sample noise)
        ProjectileTypeConfig bobber = ProjectileTypeConfig.builder(base.typeConfig(FishingBobber.KEY))
                .speed(1.51125)
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
        ProjectileTypeConfig snowball = thrown(ProjectileTypeConfig.builder(Snowball.KEY));
        ProjectileTypeConfig egg = thrown(ProjectileTypeConfig.builder(Egg.KEY));
        ProjectileTypeConfig pearl = thrown(ProjectileTypeConfig.builder(base.typeConfig(Pearl.KEY))
                .behavior(PEARL_TELEPORT));
        // capture 2026-07-28 (78 arrows): deterministic - the vanilla spread gaussian pinned to +1 (dir += 0.0075
        // per axis pre-scale; 3 consecutive same-aim shots byte-identical, straight-up = (+179, 24179, +179) wire)
        ProjectileTypeConfig arrow = ProjectileTypeConfig.builder(base.typeConfig(Arrow.KEY))
                .spread(0.0).spreadBias(0.0075)
                .knockback(Knockback.arrow()).build();
        return ProjectileConfig.builder(base)
                // the 0.05 wire motY floor is universal on minemen projectiles, so make it the generic default every type inherits
                .defaults(ProjectileTypeConfig.builder(base.defaults()).wireMotYFloor(WIRE_MOTY_FLOOR).build())
                .typeConfigs(fireball, splash, bobber, snowball, egg, pearl, arrow)
                .shootables(new PseudoHook.Installer())
                .useItemAimSync(true) // MineMen launches on the CLICK-time aim (in-game: flick-throws never desync)
                .build();
    }

    // capture 2026-07-06: vanilla launch/flight, zero spread (the wire motY floor is the config-wide default above)
    private static ProjectileTypeConfig thrown(ProjectileTypeConfig.Builder builder) {
        return builder.spread(0.0).knockback(Knockback.projectile()).build();
    }
}
