package test.presets;

import io.github.term4.minestommechanics.Vanilla18;
import io.github.term4.minestommechanics.mechanics.attack.AttackConfig;
import io.github.term4.minestommechanics.mechanics.damage.DamageConfig;
import io.github.term4.minestommechanics.mechanics.knockback.KnockbackConfig;
import io.github.term4.minestommechanics.mechanics.knockback.KnockbackConfigResolver;
import io.github.term4.minestommechanics.mechanics.knockback.KnockbackComponent;
import io.github.term4.minestommechanics.tracking.ArcSpec;
import io.github.term4.minestommechanics.tracking.Physics;
import io.github.term4.minestommechanics.tracking.SprintTracker;
import io.github.term4.minestommechanics.tracking.VelocityContext;
import io.github.term4.minestommechanics.tracking.VelocityRule;
import io.github.term4.minestommechanics.util.Directions;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.Player;
import org.jetbrains.annotations.Nullable;

// TODO: Try with piecewise function of some sort

public final class Minemen {
    // TODO: Disable damage override when both the initial damage and replacement damage are melee dealt with the same item.
    private Minemen() {}

    /** Returns AttackConfig based on Vanilla18 with a 1-tick hit queue buffer against the damage invul window. */
    public static AttackConfig atk() {
        return AttackConfig.builder(Vanilla18.atk())
                .hitQueueBuffer(1)
                .hitQueueInvulSource(AttackConfig.HitQueueInvulSource.DAMAGE)
                .build();
    }

    /** Returns DamageConfig based on Vanilla18 with overdamage enabled and applied silently (no hurt animation). */
    public static DamageConfig dmg() {
        return DamageConfig.builder(Vanilla18.dmg())
                .overdamageSilent(true)
                .build();
    }

    // TODO: Double check this with our new velocity stuff
    public static KnockbackConfig kb() {
        return KnockbackConfig.builder(Vanilla18.kb())
                .velocity(VICTIM_VEL)
                .sprintBuffer(SPRINT_BUFFER)
                .horizontal(0.5274)
                .vertical(VERTICAL_BASE)
                .extraHorizontal(0.3271)
                .extraVertical(0.0)
                .verticalBounds(0.050, VERTICAL_CAP)
                .verticalLaunchHold(-VANILLA.jumpVelocity()) // -0.42 = -jumpVelocity: cap holds through air-tick 5, releases into decay at tick 6 (walk-off feeds v6)
                .yawWeight(0.5)
                .extraYawWeight(0.5)
                .frictionH(0.0)
                .frictionV(VERTICAL_FRIC) // = N*drag (base = CAP + g/N): exact wire-shorts over no-jump ticks 6-41 + jump ticks 13-19
                                        // TODO Either change base rr formula in calculator to the nnew one, or add a custom module
                                        // ALSO rr needs to happen after, so maybe add an ordering method to kb configs? We could
                                        // just append after, but then whenever we want custom modules it'll be annoying to use rr.
                .rangeStartExtraH(3.0) //
                .rangeFactorExtraH(0.35) //
                .rangeMaxH(0.3797) //
                .addCustomComponent(Minemen::axialFriction)
                .build();
    }

    /**
     * Vertical KB decay from a single integer N (= 7) and vanilla 1.8 physics:
     * {@code KB_Y = CAP + (v - v1)/(N*d)} with {@code v1 = -g*d = -0.0784} - the decay line extrapolates back
     * to the cap at the first gravity tick, so {@code frictionV = N*drag} and {@code base = CAP + gravity/N}.
     * N = 7 is the only integer reproducing the empirical wire-shorts (no-jump ticks 6-41, jump 13-19) exactly.
     */
    private static final Physics VANILLA = Physics.vanilla();
    private static final int VERTICAL_DECAY_N = 7;
    private static final double VERTICAL_CAP = 0.3614;
    private static final double VERTICAL_BASE = VERTICAL_CAP + VANILLA.gravity() / VERTICAL_DECAY_N;        // 0.3728286
    private static final double VERTICAL_FRIC = VERTICAL_DECAY_N * VANILLA.verticalAirResistance();         // 6.86

    /** Recent-sprint window (ticks) for both the sprint knockback and the axial drag's victim gate. */
    private static final int SPRINT_BUFFER = 8;
    /** Vanilla sprint-jump horizontal impulse (blocks/tick) - the victim's reconstructed sprint speed while sprinting. */
    private static final double SPRINT_JUMP_IMPULSE = 0.2;
    /** Axial-drag coefficient - this term's own "frictionH", separate from the config's: {@code 0.475 * 0.2 = 0.095}. */
    private static final double AXIAL_iFH = 0.475;
    /**
     * Victim velocity, used by both the friction term and the axial drag. Horizontal = {@link #sprintVel quantized
     * sprint reconstruction}; vertical = server-side air-tick gravity arc (ping-invariant - MMC's vertical KB is
     * identical across pings, so it cannot be client position-delta - the fall that drives friction-V). Horizontal
     * is inert for friction (Minemen's {@code frictionH = 0}); it rides along only as the axial drag's speed source.
     */
    private static final VelocityRule VICTIM_VEL = VelocityRule.split(Minemen::sprintVel,
            VelocityRule.simulated(ArcSpec.builder()
                    .verticalStyle(VelocityRule.ArcStyle.PER_TICK)
                    .launchOffset(VelocityRule.VANILLA_LAUNCH_OFFSET)
                    .build()));

    /**
     * Quantized victim sprint velocity: the flat sprint-jump {@link #SPRINT_JUMP_IMPULSE} along the victim's facing
     * while its client was sprinting within {@link #SPRINT_BUFFER} ticks, else zero. The single place to fold in any
     * future per-victim sprint-speed modifier (speed effects, attribute scaling, ...).
     */
    private static Vec sprintVel(VelocityContext ctx) {
        return ctx.wasClientRecentlySprinting(SPRINT_BUFFER)
                ? Directions.fromYaw(ctx.entity().getPosition().yaw()).mul(SPRINT_JUMP_IMPULSE)
                : Vec.ZERO;
    }

    /**
     * Minemen axial sprint drag: a {@code +-(AXIAL_iFH * sprintSpeed)} push locked to one cardinal axis, summed onto
     * the final knockback - a drag opposing the victim's sprint momentum. It's snapped to an axis (unlike the linear
     * {@code frictionH} term), which is why it's its own {@link KnockbackComponent}. Today {@code 0.475 * 0.2 = 0.095},
     * but any sprint-speed change in {@link #VICTIM_VEL} scales it.
     *
     * <p><b>Gating.</b> Self-gated to a melee hit by a recently {@link SprintTracker#wasRecentlySprinting sprinting}
     * attacker; the victim-sprinting gate is folded into {@link #VICTIM_VEL} (zero speed -> no contribution). The
     * source/non-melee guards also cover the eager resolve pass (components run on every knockback, even fall/fire).
     *
     * <p><b>Direction.</b> The axis is the attacker's snapped facing (~the push axis on a hit), so it never lands on a
     * perpendicular. Only the sign flips, opposing the victim's sprint velocity along that axis: facing toward the
     * attacker adds ({@code ~0.8545 -> ~0.9495}), fleeing subtracts ({@code ~0.8545 -> ~0.7595}).
     */
    @Nullable
    private static Vec axialFriction(KnockbackConfigResolver.KnockbackContext ctx) {
        var snap = ctx.snap();
        Entity attacker = snap.source();
        Entity target = snap.target();
        if (attacker == null || !snap.cause().isMelee()) return null;
        var tracker = ctx.services().sprintTracker();
        if (!SprintTracker.wasRecentlySprinting(tracker, attacker, SPRINT_BUFFER)) return null;     // sprint hit

        // victim's reconstructed horizontal sprint velocity, off the same rule the config uses (zero -> client wasn't
        // sprinting -> no drag); vertical component is the friction term's fall delta, ignored here
        Vec vel = VICTIM_VEL.estimate(VelocityContext.of(target, tracker));
        double speed = Math.hypot(vel.x(), vel.z());
        if (speed <= 0) return null;

        var aPos = attacker.getPosition();

        // Using attacker yaw is technically lower resolution than using push, and more inconsistent at ~0, 90, etc, but i think
        // that's honestly more consistent with MineMen
        Vec axis = Directions.snapDominantAxis(Directions.fromYaw(aPos.yaw()));

        /*
        // Could also be this, they're nearly identical. The following method suffers less from knockback displacement

        var tPos = target.getPosition();
        Vec push = new Vec(tPos.x() - aPos.x(), 0, tPos.z() - aPos.z()); // attacker -> target (base push dir)
        if (push.lengthSquared() < 1e-9) return null;
        Vec axis = Directions.snapDominantAxis(push);
         */

        double sign = vel.dot(axis) <= 0 ? 1.0 : -1.0;
        return axis.mul(sign * AXIAL_iFH * speed);
    }

    /** Returns ping-based rangeStart value, or null if source is not a Player. */
    @Nullable
    private static Double startFromPing(KnockbackConfigResolver.KnockbackContext ctx) {
        if (ctx.snap().source() instanceof Player attacker) {
            double aPing = attacker.getLatency();
            double scale = aPing / 200;
            double value = 3.75 - scale * 0.75;
            System.out.println(value);
            return value;  // tune these values
        }
        return null;
    }

    /** Returns ping-based rangeFactor value, or null if source is not a Player. */
    @Nullable
    private static Double factorFromPing(KnockbackConfigResolver.KnockbackContext ctx) {
        if (ctx.snap().source() instanceof Player attacker) {
            double aPing = attacker.getLatency();
            double scale = aPing / 200;
            double value = 0.433 - scale * 0.2;
            System.out.println(value);
            return value;  // tune these values
        }
        return null;
    }

    /** Returns ping-based rangeMax value, or null if source is not a Player. */
    @Nullable
    private static Double maxFromPing(KnockbackConfigResolver.KnockbackContext ctx) {
        if (ctx.snap().source() instanceof Player attacker) {
            double aPing = attacker.getLatency();
            double scale = aPing / 200;
            double value = 0.45 - scale * 0.25;
            System.out.println(value);
            return value;  // tune these values
        }
        return null;
    }

}