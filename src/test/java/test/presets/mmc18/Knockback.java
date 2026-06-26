package test.presets.mmc18;

import io.github.term4.minestommechanics.mechanics.knockback.KnockbackComponent;
import io.github.term4.minestommechanics.mechanics.knockback.KnockbackConfig;
import io.github.term4.minestommechanics.mechanics.knockback.KnockbackConfigResolver;
import io.github.term4.minestommechanics.mechanics.knockback.KnockbackSystem;
import io.github.term4.minestommechanics.tracking.SprintTracker;
import io.github.term4.minestommechanics.tracking.motion.VelocityConfig;
import io.github.term4.minestommechanics.util.Directions;
import io.github.term4.minestommechanics.util.tick.TickScaler;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import org.jetbrains.annotations.Nullable;

/**
 * mmc18 knockback: the vanilla 1.8 melee base ({@link io.github.term4.minestommechanics.mechanics.vanilla18.Knockback})
 * retuned to MineMen's values, plus the vertical cap-hold, axial sprint drag, and range-reduction components.
 */
public final class Knockback {

    private Knockback() {}

    /** Recent-sprint window (ticks) for both the sprint knockback and the axial drag's victim gate (also read by {@link Movement}). */
    static final int SPRINT_BUFFER = 5;

    /**
     * mmc18 melee knockback. Velocity is NOT pinned here - it reads from the scope, so pair this with
     * {@code MechanicsProfile.velocity(}{@link Movement#velocity()}{@code )} (the friction fold AND the custom components
     * below read the one scoped rule via {@code ctx.victimVelocity()}).
     */
    public static KnockbackConfig melee() {
        return KnockbackConfig.builder(io.github.term4.minestommechanics.mechanics.vanilla18.Knockback.melee())
                .sprintBuffer(SPRINT_BUFFER)
                .horizontal(0.5274)
                .vertical(VERTICAL_BASE)
                .extraHorizontal(0.3271)
                .extraVertical(0.0)
                .verticalBounds(0.050, VERTICAL_CAP)
                .yawWeight(0.5)
                .extraYawWeight(0.5)
                .frictionH(0.0)
                .frictionV(VERTICAL_FRIC)
                .addCustomComponent(Knockback::verticalLaunchHold)
                .addCustomComponent(Knockback::axialFriction)
                .addCustomComponent(Knockback::rangeReduction)
                .build();
    }

    /**
     * Vertical KB decay from a single integer N (= 7) and vanilla 1.8 physics:
     * {@code KB_Y = CAP + (v - v1)/(N*d)} with {@code v1 = -g*d = -0.0784} - the decay line extrapolates back
     * to the cap at the first gravity tick, so {@code frictionV = N*drag} and {@code base = CAP + gravity/N}.
     * N = 7 is the only integer reproducing the empirical wire-shorts (no-jump ticks 6-41, jump 13-19) exactly.
     */
    private static final int VERTICAL_DECAY_N = 7;
    private static final double VERTICAL_CAP = 0.3614;
    private static final double VERTICAL_BASE = VERTICAL_CAP + VelocityConfig.GRAVITY / VERTICAL_DECAY_N;   // 0.3728286
    private static final double VERTICAL_FRIC = VERTICAL_DECAY_N * VelocityConfig.DRAG_V;                   // 6.86

    /** Axial-drag coefficient - this term's own "frictionH", separate from the config's: {@code 0.475 * 0.2 = 0.095}. */
    private static final double AXIAL_iFH = 0.475;

    /** Cap-hold release threshold (b/t): the fold's vertical must fall below this before the cap releases. */
    private static final double VERTICAL_HOLD_RELEASE = -VelocityConfig.JUMP_VELOCITY; // -0.42: holds through air-tick 5, releases into decay at tick 6 (walk-off feeds v6)

    /**
     * mmc18 vertical launch cap-hold: while the victim's launch arc is still rising / barely falling (the
     * {@link Movement#velocity() reconstructed} vertical velocity above {@link #VERTICAL_HOLD_RELEASE}), vertical
     * knockback is pinned to {@link #VERTICAL_CAP} instead of being sagged by the friction term - what makes a jump's cap
     * hold longer than a walk-off's. Releases into the normal {@code base + v/frictionV} decay once the fall builds past
     * the threshold.
     */
    @Nullable
    private static Vec verticalLaunchHold(KnockbackConfigResolver.KnockbackContext ctx, Vec kb) {
        double vy = ctx.victimVelocity().y();
        if (vy <= VERTICAL_HOLD_RELEASE) return null;
        return new Vec(kb.x(), VERTICAL_CAP, kb.z());
    }

    /**
     * mmc18 axial sprint drag: a {@code +-(AXIAL_iFH * sprintSpeed)} push locked to one cardinal axis, summed onto the
     * final knockback - a drag opposing the victim's sprint momentum. It's snapped to an axis (unlike the linear {@code
     * frictionH} term), which is why it's its own {@link KnockbackComponent}. Today {@code 0.475 * 0.2 = 0.095}, but any
     * sprint-speed change in {@link Movement#velocity()} scales it.
     *
     * <p><b>Gating.</b> Self-gated to a melee hit by a recently {@link SprintTracker#wasRecentlySprinting sprinting}
     * attacker; the victim-sprinting gate is folded into the velocity rule (zero speed -> no contribution). The
     * source/non-melee guards also cover the eager resolve pass (components run on every knockback, even fall/fire).
     *
     * <p><b>Direction.</b> The axis is the attacker's snapped facing (~the push axis on a hit), so it never lands on a
     * perpendicular. Only the sign flips, opposing the victim's sprint velocity along that axis.
     */
    @Nullable
    private static Vec axialFriction(KnockbackConfigResolver.KnockbackContext ctx, Vec kb) {
        var snap = ctx.snap();
        Entity attacker = snap.source();
        if (attacker == null || !snap.melee()) return null;
        var tracker = ctx.services().sprintTracker();
        if (!SprintTracker.wasRecentlySprinting(tracker, attacker, TickScaler.duration(SPRINT_BUFFER, KnockbackSystem.KEY))) return null;     // sprint hit

        Vec vel = ctx.victimVelocity();
        double speed = Math.hypot(vel.x(), vel.z());
        if (speed <= 0) return null;

        var aPos = attacker.getPosition();

        // Using attacker yaw is technically lower resolution than using push, and more inconsistent at ~0, 90, etc, but i think
        // that's honestly more consistent with MineMen
        Vec axis = Directions.snapDominantAxis(Directions.fromYaw(aPos.yaw()));

        double sign = vel.dot(axis) <= 0 ? 1.0 : -1.0;
        return kb.add(axis.mul(sign * AXIAL_iFH * speed));
    }

    /** Range limit line: max horizontal KB (b/t) = {@link #RANGE_LIMIT_BASE} - distance x {@link #RANGE_FACTOR}. */
    private static final double RANGE_LIMIT_BASE = 2.0;
    private static final double RANGE_FACTOR = 0.35;
    /** Floor of the range limit: no distance can cap horizontal KB below this (empirical; above the 0.5274 base - fine). */
    private static final double RANGE_LIMIT_MIN = 0.5674;

    /**
     * mmc18 range reduction - a linear <em>limit</em> on knockback by distance, not a scale-down: the horizontal
     * magnitude is capped at {@code max(RANGE_LIMIT_BASE - distance * RANGE_FACTOR, RANGE_LIMIT_MIN)} - so close hits are
     * untouched, far-reach hits cannot exceed the line, and the {@link #RANGE_LIMIT_MIN} floor keeps long-range hits from
     * being crushed toward zero. Runs LAST (after {@link #axialFriction} and every other stage) - a final cap on the whole
     * vector; direction and vertical are preserved.
     */
    @Nullable
    private static Vec rangeReduction(KnockbackConfigResolver.KnockbackContext ctx, Vec kb) {
        var snap = ctx.snap();
        Entity attacker = snap.source();
        Entity target = snap.target();
        if (attacker == null || target == null || !snap.melee()) return null;

        var aPos = attacker.getPosition();
        var tPos = target.getPosition();
        double dist = Math.hypot(tPos.x() - aPos.x(), tPos.z() - aPos.z());
        double limit = Math.max(RANGE_LIMIT_BASE - dist * RANGE_FACTOR, RANGE_LIMIT_MIN);
        double hMag = Math.hypot(kb.x(), kb.z());
        if (hMag <= limit) return null; // under the line - untouched
        double s = limit / hMag;
        return new Vec(kb.x() * s, kb.y(), kb.z() * s);
    }
}
