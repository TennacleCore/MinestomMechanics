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
import net.minestom.server.network.packet.server.play.EntityPositionSyncPacket;
import net.minestom.server.network.packet.server.play.SpawnEntityPacket;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Primed TNT on 1.8 physics (captures 2026-07-06/07; mmc18-explosion-model memory): vanilla kick from
 * blockY+0.5, gravity-first moves, and the live vanilla ground lines - their move() preserves motY through
 * collisions, so the {@code motY *= -0.5} flip and {@code *0.7} friction run as written. Preset knobs ride
 * {@link Config} (fuse, power, detonation height, wire shape); blast-push retunes are the presets' own
 * {@code ExplosionEvent} listeners.
 */
public final class PrimedTnt extends Entity {

    /** {@code detonateAtFeet}: MineMen detonates at the feet, vanilla/Hypixel at {@code +height/16}. */
    public record Config(int fuseTicks, float power, boolean detonateAtFeet, Wire wire) {}

    /**
     * Measured tracker shapes. {@code MINEMEN}: etp+vel on 10t boundaries while moving, silence at rest,
     * grounded tracker vy floored +0.05 (on-blast sends stay raw). {@code HYPIXEL}: velocity every 3 ticks
     * while falling, one position correction at tick 3, a final velocity on landing.
     */
    public enum Wire { MINEMEN, HYPIXEL }

    private static final int MINEMEN_SYNC_TICKS = 10;
    private static final double MINEMEN_VY_FLOOR = 0.05;
    private static final int HYPIXEL_VELOCITY_INTERVAL = 3;
    private static final int HYPIXEL_TELEPORT_TICK = 3;
    private static final double TPS = ServerFlag.SERVER_TICKS_PER_SECOND;

    private final ExplosionSystem explosion;
    private final Config config;
    private int fuse;
    private Vec motion = Vec.ZERO; // b/t; the vanilla pipeline runs on this - Minestom's per-tick result is overwritten
    private Point wireSyncedAt;
    private boolean rawBroadcast;
    private boolean flipPending;
    private int flightTick;
    private boolean wasAirborne = true;

    private PrimedTnt(ExplosionSystem explosion, Config config) {
        super(EntityType.TNT);
        this.explosion = explosion;
        this.config = config;
        this.fuse = config.fuseTicks();
        setTag(ProjectileEntity.PROJECTILE_COLLIDABLE, true); // fireballs detonate on it, arrows deflect off
        // registry TNT physics = the 1.8 constants (bb 0.98, gravity 0.04, drag 0.98); the tick below reads them
        // syncs are hand-sent in update() (per-wire tracker shapes). Minestom's scheduled sync can't be
        // reused: it re-sends forever at rest, buffers the possync but sends the velocity direct (flipped
        // wire order), and latches a default-tick-20 first sync at construction that setSynchronizationTicks
        // does not reset - kill that latch.
        setSynchronizationTicks(config.fuseTicks() + 20L);
        try {
            var latch = Entity.class.getDeclaredField("nextSynchronizationTick");
            latch.setAccessible(true);
            latch.setLong(this, Long.MAX_VALUE);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /**
     * data=1 + real velocity: with data 0 the 1.8 spawn can't carry velocity, so ViaRewind splits it into a
     * scheduled SET_ENTITY_MOTION - our embedded zero then lands mid-hop and kills the client's kick
     * prediction. 1.8 and 26.1 clients both ignore the data value for TNT.
     */
    @Override
    protected SpawnEntityPacket getSpawnPacket() {
        Pos position = getPosition();
        return new SpawnEntityPacket(getEntityId(), getUuid(), getEntityType(), position, position.yaw(), 1, getVelocityForPacket());
    }

    /** Spawns at the TNT block's {@code +0.5,+0.5,+0.5} (half a block up - measured) with the vanilla kick. */
    public static PrimedTnt spawn(ExplosionSystem explosion, Instance instance, Point tntBlock, Config config) {
        PrimedTnt tnt = new PrimedTnt(explosion, config);
        double angle = ThreadLocalRandom.current().nextDouble() * Math.PI * 2.0;
        tnt.setVelocity(new Vec(-Math.sin(angle) * 0.02, 0.2, -Math.cos(angle) * 0.02).mul(TPS));
        tnt.setInstance(instance, new Pos(tntBlock.blockX() + 0.5, tntBlock.blockY() + 0.5, tntBlock.blockZ() + 0.5));
        tnt.wireSyncedAt = tnt.getPosition();
        return tnt;
    }

    @Override
    public void setVelocity(@NotNull Vec velocity) {
        this.motion = velocity.div(TPS); // external pushes (blast impulses) join the pipeline
        rawBroadcast = true;             // on-blast sends carry the raw impulse (their direct-hit wire shows vy -0.31
        super.setVelocity(velocity);     // unfloored); the +0.05 floor is tracker-cadence only
        rawBroadcast = false;
        this.velocity = moveVector();    // silent overwrite after the broadcast
        // a downward impulse into an already-grounded TNT bounces at ANY magnitude (their fireball-hit TNT
        // took vy -0.31 and skipped 4.4 blocks airborne); the -0.6 gate below is for LANDING ticks only,
        // where the collision normally zeroes motY
        flipPending = isOnGround() && motion.y() < 0;
    }

    /** Vanilla motY state (the wire-visible value), not the Minestom move vector. */
    @Override
    public @NotNull Vec getVelocity() {
        return motion.mul(TPS);
    }

    // vanilla applies gravity BEFORE the move; handing Minestom the raw motion made the spawn hop apex
    // +0.577 instead of vanilla's +0.386 - high enough to climb adjacent blocks vanilla TNT never clears
    private Vec moveVector() {
        return motion.sub(0, getAerodynamics().gravity(), 0).mul(TPS);
    }

    @Override
    public void update(long time) {
        // vanilla 1.8 TNT tick on our own motion. The x-0.5 flip: on LANDING ticks only HARD impacts carry
        // motY through the collision (-0.72 bounced wire-exact +0.354; -0.42 and the spawn hop's -0.27
        // rested - the collision zeroes motY), but a downward impulse into an ALREADY-GROUNDED TNT flips at
        // any magnitude (flipPending - their fireball-hit TNT bounced from vy -0.31). Once grounded the flip
        // runs every tick, converging stored motY to +0.013154 = -0.49*(m-0.04)'s fixed point - blast kicks
        // are ADDED to it, and the 16-13-13 fireball-launch capture reads that additive in every delivery
        // (the old -0.0392 rest probe cost every grounded launch 0.052 of vertical). Upward pushes pass
        // through; the settled cycle's move vector stays downward (+0.0132-0.04), so Minestom's onGround holds.
        Aerodynamics aero = getAerodynamics();
        double vy = (motion.y() - aero.gravity()) * aero.verticalAirResistance();
        double f = isOnGround() ? 0.7 : 1.0;
        if (isOnGround()) vy = vy < -0.6 || (flipPending && vy < 0) ? vy * -0.5
                : vy > 0 ? vy
                : wasAirborne ? 0.0
                : vy * -0.5;
        flipPending = false;
        double hDrag = aero.horizontalAirResistance() * f;
        motion = new Vec(motion.x() * hDrag, vy, motion.z() * hDrag);
        this.velocity = moveVector(); // silent; the wire is the per-style sync + push sends

        switch (config.wire()) {
            case MINEMEN -> {
                // measured wire: etp+vel ride ticks 11/21/31/... while moving, nothing at rest
                long t = getAliveTicks();
                if (t >= MINEMEN_SYNC_TICKS && t % MINEMEN_SYNC_TICKS == 0) minemenSyncIfMoved();
            }
            case HYPIXEL -> hypixelWire();
        }
        wasAirborne = !isOnGround();

        if (--fuse > 0) return;
        Instance instance = getInstance();
        if (instance != null) {
            Point center = config.detonateAtFeet() ? getPosition()
                    : getPosition().add(0, getBoundingBox().height() / 16.0, 0); // vanilla +height/16
            explosion.explode(instance, center, config.power(), this);
        }
        remove();
    }

    // 1.8 tracker gate: sync only when the fixed-point wire position moved
    private void minemenSyncIfMoved() {
        Pos pos = getPosition();
        if (wireSyncedAt != null && Math.abs(pos.x() - wireSyncedAt.x()) < 1.0 / 32
                && Math.abs(pos.y() - wireSyncedAt.y()) < 1.0 / 32 && Math.abs(pos.z() - wireSyncedAt.z()) < 1.0 / 32) return;
        sendSync(pos);
    }

    // one position correction a few ticks after spawn + velocity every few ticks while falling (the 1.8
    // client predicts the arc; Minestom's relative moves don't survive Via), then a final ~0 velocity on landing
    private void hypixelWire() {
        boolean airborne = !isOnGround();
        if (airborne) {
            if (flightTick == HYPIXEL_TELEPORT_TICK) sendSync(getPosition());
            else if (flightTick % HYPIXEL_VELOCITY_INTERVAL == 0) sendPacketToViewersAndSelf(getVelocityPacket());
            flightTick++;
        } else if (wasAirborne) {
            sendPacketToViewersAndSelf(getVelocityPacket());
        }
    }

    private void sendSync(Pos pos) {
        Point delta = wireSyncedAt == null ? Vec.ZERO : pos.sub(wireSyncedAt);
        wireSyncedAt = pos;
        sendPacketToViewers(new EntityPositionSyncPacket(getEntityId(), pos, delta, pos.yaw(), pos.pitch(), isOnGround()));
        sendPacketToViewers(getVelocityPacket());
    }

    /** MineMen's grounded tracker velocity floors vy at +0.05 (their motY floor); the sim is untouched. */
    @Override
    protected Vec getVelocityForPacket() {
        Vec v = motion; // the vanilla state, not the pre-move-gravity move vector
        return config.wire() == Wire.MINEMEN && !rawBroadcast && isOnGround() && v.y() < MINEMEN_VY_FLOOR
                ? v.withY(MINEMEN_VY_FLOOR) : v;
    }
}
