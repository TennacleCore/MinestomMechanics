package io.github.term4.minestommechanics.tracking;

import net.minestom.server.entity.Entity;
import org.jetbrains.annotations.Nullable;

/**
 * Strategy for "is this entity on the ground" - the single predicate every ground-gated mechanic reads: the
 * {@link VelocityRule#simulated(ArcSpec) simulated} arc's air clock, {@link MotionTracker#isGrounded},
 * {@link MotionTracker#isFalling}, and so the knockback {@code victimOnGround} / attacker-crit checks built on them.
 * A single-method interface with a {@link #DEFAULT} and static factories, mirroring {@link VelocityRule} and
 * {@code AttackEvent.CriticalRule}.
 *
 * <p>Two base rules:
 * <ul>
 *   <li>{@link #CLIENT} - Minestom's client-authoritative {@link Entity#isOnGround()} flag. Cheap, but a laggy
 *       client lags it behind its true landing, so a falling victim still reads airborne for a tick or two after it
 *       has really landed - the "air knockback on a target that's actually grounded" bug.</li>
 *   <li>{@link #collision()} - vanilla {@code Entity.move()}: the {@link #CLIENT} flag <em>OR</em> a server-side
 *       collision sweep down the victim's real fall ({@link MotionTracker#isGroundedByCollision}). The sweep leads
 *       the flag exactly as vanilla's collision-derived {@code onGround} leads the client's {@code onGround} packet,
 *       so it stays correct under lag while still trusting the flag for the resting case.</li>
 * </ul>
 *
 * <p>Resolved per entity via {@link MotionTracker#resolveGroundRule}: a per-entity
 * {@link MotionTracker#setGroundRule override} wins, else a velocity-mode's {@link ArcSpec#groundRule()}, else
 * {@link #DEFAULT}.
 */
@FunctionalInterface
public interface GroundRule {

    /** Whether {@code entity} is on the ground. */
    boolean isOnGround(@Nullable Entity entity);

    /** Minestom's client-authoritative {@link Entity#isOnGround()} flag (whatever the client last reported). */
    GroundRule CLIENT = entity -> entity != null && entity.isOnGround();

    /**
     * Vanilla {@code Entity.move()} ground test with default {@link GroundSpec} knobs: the {@link #CLIENT} flag OR a
     * server-side collision sweep down the victim's real fall. Stays correct while a laggy client's {@code onGround}
     * packet is still in flight.
     */
    static GroundRule collision() { return collision(GroundSpec.defaults()); }

    /** {@link #collision()} with explicit probe knobs (floor / margin / physics). */
    static GroundRule collision(GroundSpec spec) {
        return entity -> CLIENT.isOnGround(entity) || MotionTracker.isGroundedByCollision(entity, spec);
    }

    /**
     * Rule used when nothing else specifies one. Vanilla-faithful {@link #collision()}: this library recreates
     * vanilla mechanics, and vanilla gates its air clock on the {@code move()} collision result rather than the raw
     * client {@code onGround} flag.
     */
    GroundRule DEFAULT = collision();
}
