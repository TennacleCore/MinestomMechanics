package io.github.term4.minestommechanics.tracking;

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
/** Player motion tracker (position-delta velocity + jump detection). Minestom's getVelocity does not
 * work for clients, so per-tick motion is derived from position deltas with jump detection and
 * sprint-jump impulse injection. This is the move/input-clock data source behind {@link VelocityContext};
 * the ground/air timeline (ticks-in-air, launch arc) lives in {@link GroundTracker}, and the
 * estimation strategies that read all of it live in {@link VelocityRule}. */
public final class MovementTracker {

    private record Frame(Vec velocity, Pos pos, long tick, boolean onGround) {}
    private record JumpStamp(long tick, double yaw, boolean wasSprinting) {}
    private static final Tag<Frame> FRAME = Tag.Transient("mm:velocity-frame");
    private static final Tag<JumpStamp> LAST_JUMP = Tag.Transient("mm:last-jump");
    private static final Tag<Boolean> RESET = Tag.Transient("mm:velocity-reset");
    private static final long GAP_TICKS = 6; // Ticks after which client assumed to be lag spiking (ignore first delta due to perceived teleport)
    private static final long SPRINT_DECAY_TICKS = 3; // Ticks after which client is assumed stationary (velocity = 0)
    private static final long JUMP_DECAY_TICKS = 10; // Ticks after which client is no longer counted as having jumped (velocity = 0)

    private MovementTracker() {}

    /** Install listeners for motion tracking. */
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

    static void onMove(Player p, Pos pos, long tick, boolean onGround) {
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
                    Vec h = wasSprinting ? VelocityContext.sprintJumpImpulse(pos.yaw()) : Vec.ZERO;
                    velocity = new Vec(h.x(), VelocityContext.JUMP_Y, h.z());
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

    /** Position-delta tracked velocity (blocks/tick); players via the tracker, others via entity velocity. */
    static Vec tracked(Entity entity) {
        return entity instanceof Player p ? get(p) : entity.getVelocity();
    }

    /** Most recent jump-key press within {@link #JUMP_DECAY_TICKS}, or {@code null}. */
    static VelocityContext.JumpInfo recentJump(Entity entity) {
        if (!(entity instanceof Player p)) return null;
        JumpStamp j = p.getTag(LAST_JUMP);
        if (j == null || (p.getAliveTicks() - j.tick()) > JUMP_DECAY_TICKS) return null;
        return new VelocityContext.JumpInfo(j.yaw(), j.wasSprinting());
    }
}
