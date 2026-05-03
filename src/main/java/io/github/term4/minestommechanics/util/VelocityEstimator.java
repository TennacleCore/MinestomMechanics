package io.github.term4.minestommechanics.util;

import io.github.term4.minestommechanics.mechanics.knockback.KnockbackConfig;
import net.minestom.server.ServerFlag;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.Player;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.entity.EntityTeleportEvent;
import net.minestom.server.event.player.PlayerInputEvent;
import net.minestom.server.event.player.PlayerMoveEvent;
import net.minestom.server.tag.Tag;
import org.jetbrains.annotations.NotNull;

// TODO: Update tick based values to use tps scaling (util class)
// TODO: Update air tick 
/** Position delta velocity estimation for players. Minestom's getVelocity does not work for clients.
 * Predicts motion using jump detection and sprint-jump impulse injection. */
public final class VelocityEstimator {

    private static final double TPS = ServerFlag.SERVER_TICKS_PER_SECOND;

    // Vanilla-like constants (tune per version)
    private static final double BASE_JUMP_Y = 0.42;
    private static final double SPRINT_JUMP_HORIZONTAL = 0.2;
    private static final double GRAVITY_PER_TICK = 0.08;
    private static final double TERMINAL_VY = -3.92;

    private record Frame(Vec velocity, Pos pos, long tick, boolean onGround) {}
    private record JumpStamp(long tick, double yaw, boolean wasSprinting) {}
    private static final Tag<Frame> FRAME = Tag.Transient("mm:velocity-frame");
    private static final Tag<JumpStamp> LAST_JUMP = Tag.Transient("mm:last-jump");
    private static final Tag<Boolean> RESET = Tag.Transient("mm:velocity-reset");
    private static final long GAP_TICKS = 6; // Ticks after which client assumed to be lag spiking (ignore first delta due to perceived teleport)
    private static final long SPRINT_DECAY_TICKS = 3; // Ticks after which client is assumed stationary (velocity = 0)
    private static final long JUMP_DECAY_TICKS = 10; // Ticks after which client is no longer counted as having jumped (velocity = 0)

    private VelocityEstimator() {}

    /** Install listeners for velocity estimation. */
    public static void install(EventNode<@NotNull Event> node) {
        // Jump detection from explicit input (avoids treating knockback as jump)
        node.addListener(PlayerInputEvent.class, e -> {
            if (e.hasPressedJumpKey()) {
                var p = e.getPlayer();
                p.setTag(LAST_JUMP, new JumpStamp(p.getAliveTicks(), p.getPosition().yaw(), p.isSprinting()));
            }
        });

        // Normal movement
        node.addListener(PlayerMoveEvent.class, e -> {
            onMove(e.getPlayer(), e.getNewPosition(), e.getPlayer().getAliveTicks(), e.isOnGround());
        });

        // Teleport handling
        node.addListener(EntityTeleportEvent.class, e -> {
            if (e.getEntity() instanceof Player p) {
                reset(p, e.getNewPosition());
            }
        });
    }

    public static void onMove(Player p, Pos pos, long tick, boolean onGround) {
        Frame prev = p.getTag(FRAME);
        Vec velocity;

        if (Boolean.TRUE.equals(p.getTag(RESET))) {
            p.removeTag(RESET);
            velocity = Vec.ZERO;
        } else if (prev != null) {
            long dt = tick - prev.tick();

            if (dt > GAP_TICKS) {
                velocity = Vec.ZERO;
            } else {
                Pos last = prev.pos();
                Vec delta = new Vec(
                        pos.x() - last.x(),
                        pos.y() - last.y(),
                        pos.z() - last.z()
                );

                // Jump detection: was on ground, now in air, Y increased
                boolean jumped = prev.onGround() && !onGround && pos.y() > last.y();

                if (jumped && dt > 0) {
                    boolean wasSprinting = p.isSprinting();
                    Vec h = wasSprinting ? sprintJumpHorizontal(pos.yaw()) : Vec.ZERO;
                    velocity = new Vec(h.x(), BASE_JUMP_Y, h.z());
                    // LAST_JUMP set only from PlayerInputEvent (explicit jump key), not position heuristic
                } else if (dt > 0) {
                    // Use delta/dt as per-tick velocity
                    velocity = new Vec(delta.x() / dt, delta.y() / dt, delta.z() / dt);
                } else {
                    velocity = prev.velocity();
                }
            }
        } else {
            velocity = Vec.ZERO;
        }

        p.setTag(FRAME, new Frame(velocity, pos, tick, onGround));
    }

    private static Vec sprintJumpHorizontal(double yaw) {
        double r = Math.toRadians(yaw);
        return new Vec(-Math.sin(r) * SPRINT_JUMP_HORIZONTAL, 0, Math.cos(r) * SPRINT_JUMP_HORIZONTAL);
    }

    private static void reset(Player p, Pos newPos) {
        p.setTag(RESET, true);
        p.removeTag(LAST_JUMP);
        p.setTag(FRAME, new Frame(Vec.ZERO, newPos, p.getAliveTicks(), p.isOnGround()));
    }

    private static Vec get(Player p) {
        Frame f = p.getTag(FRAME);
        if (f == null) return Vec.ZERO;

        long elapsed = p.getAliveTicks() - f.tick();
        if (elapsed > SPRINT_DECAY_TICKS) return Vec.ZERO;

        return f.velocity();
    }

    /** Returns estimated velocity (blocks/tick). Position-delta with jump detection and extrapolation for players. */
    public static Vec getVelocity(Entity entity) {
        return entity instanceof Player p ? get(p) : entity.getVelocity();
    }

    private static final double DEFAULT_GRAVITY_SCALE = 0.98;

    /** Returns gravity-predicted velocity (blocks/tick): vy from ticks-in-air, horizontal=0. Excludes knockback. */
    public static Vec getVelocityGravityPredicted(Entity entity) {
        return getVelocityGravityPredicted(entity, null, null);
    }

    /**
     * Overload with configurable gravity and scale.
     * Uses vanilla recursive formula: vy = vy_prev * scale - gravity each tick (not linear).
     * Closed form: vy(t) = 0.42 * scale^t - gravity * (1 - scale^t) / (1 - scale).
     */
    public static Vec getVelocityGravityPredicted(Entity entity, Double gravityPerTick, Double scale) {
        double g = gravityPerTick != null ? gravityPerTick : GRAVITY_PER_TICK;
        double s = scale != null ? scale : DEFAULT_GRAVITY_SCALE;
        int ticks = GroundTracker.getTicksInAir(entity);
        if (ticks <= 0) return new Vec(0, -g * s, 0);
        double scalePow = Math.pow(s, ticks);
        double vy = BASE_JUMP_Y * scalePow - g * (1 - scalePow) / (1 - s);
        vy = Math.max(TERMINAL_VY, Math.min(BASE_JUMP_Y, vy));
        return new Vec(0, vy, 0);
    }

    /** Returns input-only velocity (blocks/tick): 0 or sprint-jump impulse. Excludes knockback/entity velocity. */
    public static Vec getVelocityInput(Entity entity) {
        if (!(entity instanceof Player p)) return Vec.ZERO;
        JumpStamp jump = p.getTag(LAST_JUMP);
        if (jump == null || (p.getAliveTicks() - jump.tick()) > JUMP_DECAY_TICKS) {
            return Vec.ZERO;
        }
        Vec h = jump.wasSprinting() ? sprintJumpHorizontal(jump.yaw()) : Vec.ZERO;
        return new Vec(h.x(), BASE_JUMP_Y, h.z());
    }

    /** Returns velocity (blocks/tick). Uses entity.getVelocity() plus sprint-jump impulse when a recent jump is detected (players only). */
    public static Vec getVelocityLegacy(Entity entity) {
        Vec vRaw = entity.getVelocity();
        double vx = vRaw.x() / TPS , vy = vRaw.y() / TPS, vz = vRaw.z() / TPS;

        if (entity instanceof Player p) {
            JumpStamp jump = p.getTag(LAST_JUMP);
            if (jump == null || (p.getAliveTicks() - jump.tick()) > JUMP_DECAY_TICKS) {
                return new Vec(vx, vy, vz);
            }
            if (jump.wasSprinting()) {
                Vec h = sprintJumpHorizontal(jump.yaw());
                vx = h.x();
                vz = h.z();
            }
            vy += BASE_JUMP_Y;
        }
        return new Vec(vx, vy, vz);
    }

    /** Returns velocity (blocks/tick) for the given method. Used by friction term in knockback. */
    public static Vec getVelocityForMethod(Entity entity, KnockbackConfig.VelocityMethod method,
                                           Double gravityPerTick, Double gravityScale) {
        return switch (method) {
            case LEGACY -> getVelocityLegacy(entity);
            case INPUT -> getVelocityInput(entity);
            case GRAVITY_PREDICTED -> getVelocityGravityPredicted(entity, gravityPerTick, gravityScale);
            default -> getVelocity(entity);
        };
    }
}
