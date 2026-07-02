package test.presets.customItems;

import io.github.term4.minestommechanics.mechanics.explosion.ExplosionSystem;
import io.github.term4.minestommechanics.mechanics.projectile.entities.ProjectileEntity;
import net.minestom.server.ServerFlag;
import net.minestom.server.collision.Aerodynamics;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.instance.Instance;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Test primed-TNT entity (Hypixel profile): vanilla spawn kick + fall physics, a fixed fuse, then a power-4 detonation
 * through the {@link ExplosionSystem} at its rest spot. While falling it pushes velocity (a 1.8 client predicts the arc,
 * since Minestom's relative moves don't survive Via) with an occasional teleport correction; once it lands it sits the
 * fuse out quietly. Not a library feature - wired as a test item in {@link ExampleServer}.
 */
public final class PrimedTnt extends Entity {

    private static final float POWER = 4.0f;
    private static final int FUSE_TICKS = 50;       // Hypixel ~2.5s (vanilla is 80)
    private static final int VELOCITY_INTERVAL = 3; // velocity broadcast cadence while falling
    private static final int TELEPORT_TICK = 3;     // single absolute-teleport correction, a few ticks after spawn (matches Hypixel)

    private final ExplosionSystem explosion;
    private int fuse = FUSE_TICKS;
    private int flightTick;
    private boolean wasAirborne = true;

    private PrimedTnt(ExplosionSystem explosion) {
        super(EntityType.TNT);
        this.explosion = explosion;
        setBoundingBox(0.98, 0.98, 0.98);
        setTag(ProjectileEntity.PROJECTILE_COLLIDABLE, true); // vanilla ad(): fireballs detonate on it, arrows deflect off
        setAerodynamics(new Aerodynamics(0.04, 0.98, 0.98)); // vanilla TNT: gravity 0.04, drag 0.98
        setSynchronizationTicks(FUSE_TICKS + 20L); // once the one early sync fires, the next reschedules past the fuse (no landing teleport)
    }

    /** Spawns a primed TNT at {@code pos} with the vanilla {@code motY 0.2} + small random horizontal kick. */
    public static PrimedTnt spawn(ExplosionSystem explosion, Instance instance, Pos pos) {
        PrimedTnt tnt = new PrimedTnt(explosion);
        double angle = ThreadLocalRandom.current().nextDouble() * Math.PI * 2.0;
        tnt.setVelocity(new Vec(-Math.sin(angle) * 0.02, 0.2, -Math.cos(angle) * 0.02).mul(ServerFlag.SERVER_TICKS_PER_SECOND));
        tnt.setInstance(instance, pos);
        return tnt;
    }

    @Override
    public void update(long time) {
        boolean airborne = !isOnGround();
        if (airborne) { // falling: velocity every few ticks + Minestom's one position sync, forced early, so the 1.8 client sees the arc
            if (flightTick == TELEPORT_TICK)
                synchronizeNextTick(); // fires Minestom's single position sync this tick = Hypixel's one early teleport
            else if (flightTick % VELOCITY_INTERVAL == 0)
                sendPacketToViewersAndSelf(getVelocityPacket());
            flightTick++;
        } else if (wasAirborne) {
            sendPacketToViewersAndSelf(getVelocityPacket()); // one final velocity (~0) on landing, matching Hypixel's last packet
        }
        wasAirborne = airborne;
        if (--fuse > 0) return;
        Instance instance = getInstance();
        if (instance != null) {
            Point center = getPosition().add(0, getBoundingBox().height() / 16.0, 0); // vanilla detonates at posY + height/16
            explosion.explode(instance, center, POWER, this);
        }
        remove();
    }
}
