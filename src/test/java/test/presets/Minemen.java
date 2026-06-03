package test.presets;

import io.github.term4.minestommechanics.Vanilla18;
import io.github.term4.minestommechanics.mechanics.attack.AttackConfig;
import io.github.term4.minestommechanics.mechanics.damage.DamageConfig;
import io.github.term4.minestommechanics.mechanics.knockback.KnockbackConfig;
import io.github.term4.minestommechanics.mechanics.knockback.KnockbackConfigResolver;
import io.github.term4.minestommechanics.tracking.SprintTracker;
import io.github.term4.minestommechanics.tracking.VelocityRule;
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

    public static KnockbackConfig kb() {
        int buffer = 8;
        return KnockbackConfig.builder(Vanilla18.kb())
                .velocityMethod(VelocityRule.delta())
                .sprintBuffer(buffer)
                .horizontal(0.52725)
                .vertical(0.3724) // or 0.3736, 0.3724
                .extraHorizontal(0.32625, ctx -> {
                    var tracker = ctx.services().sprintTracker();
                    var snap = ctx.snap();
                    if (snap.source() != null
                            && SprintTracker.wasClientRecentlySprinting(tracker, snap.target(), buffer)) {
                        return 0.41865;
                    }
                    return null;
                })
                .extraVertical(0.0)
                .verticalBounds(0.050, 0.3615)
                .yawWeight(0.5)
                .extraYawWeight(0.5)
                .frictionH(0.0)
                .frictionV(6.82) // or 6.82, 6.884
                .useAbsFrictionV()
                .frictionExtraH(0.0)
                .frictionExtraV(6.884)
                .useAbsFrictionEV()
                .rangeStartExtraH(3.0)
                .rangeFactorExtraH(0.40)
                .rangeMaxH(0.35)
                .build();
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