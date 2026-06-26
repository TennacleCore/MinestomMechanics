package test.presets.mmc18;

import io.github.term4.minestommechanics.mechanics.knockback.KnockbackSystem;
import io.github.term4.minestommechanics.tracking.motion.VelocityConfig;
import io.github.term4.minestommechanics.tracking.motion.VelocityContext;
import io.github.term4.minestommechanics.tracking.motion.VelocityRule;
import io.github.term4.minestommechanics.util.Directions;
import io.github.term4.minestommechanics.util.tick.TickScaler;
import net.minestom.server.coordinate.Vec;

/**
 * mmc18 movement: the custom velocity rule read everywhere via {@code ctx.victimVelocity()} (the KB friction fold, the
 * axial drag, projectile momentum). The player platform config is {@link Player}.
 */
public final class Movement {

    private Movement() {}

    /** Vanilla sprint-jump horizontal impulse (blocks/tick) - the victim's reconstructed sprint speed while sprinting. */
    private static final double SPRINT_JUMP_IMPULSE = 0.2;

    /**
     * mmc18's velocity rule - the <em>general</em> velocity for every context, read everywhere via {@code
     * ctx.victimVelocity()}. A custom {@link VelocityRule#split split}: horizontal = {@link #sprintVel quantized sprint
     * reconstruction} (inert for friction - mmc18's {@code frictionH = 0} - it only feeds the axial drag's speed);
     * vertical = the server-arc gravity sim (ping-invariant, since MMC's vertical KB is identical across pings).
     *
     * <p><b>Fluid OFF:</b> {@code fluidPhysics(false)} skips the water/lava drag + buoyancy (and, since the current only
     * fires in water, the flow too), so a victim in water folds the same KB as in air. Climb (ladders/vines) and web stay ON.
     */
    private static final VelocityRule VELOCITY = VelocityRule.split(Movement::sprintVel,
            VelocityRule.simulated(VelocityConfig.builder()
                    .fluidPhysics(false)
                    .build()));

    /** mmc18's scoped velocity rule; set on a {@code MechanicsProfile.velocity(...)} scope, read everywhere via {@code ctx.victimVelocity()} (configured once, not pinned onto {@link Knockback#melee()}). */
    public static VelocityRule velocity() {
        return VELOCITY;
    }

    /**
     * Quantized victim sprint velocity: the flat sprint-jump {@link #SPRINT_JUMP_IMPULSE} along the victim's facing while
     * its client was sprinting within {@link Knockback#SPRINT_BUFFER} ticks, else zero. The single place to fold in any
     * future per-victim sprint-speed modifier (speed effects, attribute scaling, ...).
     */
    private static Vec sprintVel(VelocityContext ctx) {
        return ctx.wasClientRecentlySprinting(TickScaler.duration(Knockback.SPRINT_BUFFER, KnockbackSystem.KEY))
                ? Directions.fromYaw(ctx.entity().getPosition().yaw()).mul(SPRINT_JUMP_IMPULSE)
                : Vec.ZERO;
    }
}
