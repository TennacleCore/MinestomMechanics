package io.github.term4.minestommechanics.mechanics.knockback;

import io.github.term4.minestommechanics.Services;
import io.github.term4.minestommechanics.platform.Constants;
import io.github.term4.minestommechanics.tracking.GroundTracker;
import io.github.term4.minestommechanics.tracking.SprintTracker;
import io.github.term4.minestommechanics.tracking.VelocityRule;
import net.minestom.server.entity.Player;
import org.jetbrains.annotations.Nullable;

/** Resolves KnockbackConfig with context into plain values. */
public final class KnockbackConfigResolver {

    private KnockbackConfigResolver() {}

    public record KnockbackContext(KnockbackSnapshot snap, Services services) {
        public static KnockbackContext of(KnockbackSnapshot snap, Services services) {
            return new KnockbackContext(snap, services);
        }
        public boolean victimOnGround() {
            return GroundTracker.isGrounded(snap.target(), services.groundTracker() != null);
        }
        public boolean sprint() {
            var a = snap.source();
            return a instanceof Player p && services.sprintTracker() != null
                && SprintTracker.wasRecentlySprinting(services.sprintTracker(), p, 0);
        }
    }

    public static ResolvedKnockbackConfig resolve(KnockbackConfig config, KnockbackContext ctx) {
        KnockbackConfig cfg = config;
        if (cfg.subConfig != null) {
            KnockbackConfig sub = cfg.subConfig.apply(ctx);
            if (sub != null) cfg = sub.fromBase(cfg);
        }
        Integer kbVal = resolve(cfg.kbInvulTicks, ctx);
        if (kbVal == null && ctx.services().damage() != null) {
            kbVal = ctx.services().damage().defaultInvulTicks();
        }
        if (kbVal == null) {
            kbVal = Constants.DEFAULT_INVUL_TICKS;
        }
        return new ResolvedKnockbackConfig(
                kbVal,
                resolve(cfg.sprintBuffer, ctx),
                resolve(cfg.horizontal, ctx),
                resolve(cfg.vertical, ctx),
                resolve(cfg.extraHorizontal, ctx),
                resolve(cfg.extraVertical, ctx),
                resolve(cfg.horizontalBounds, ctx),
                resolve(cfg.verticalBounds, ctx),
                resolve(cfg.extraHorizontalBounds, ctx),
                resolve(cfg.extraVerticalBounds, ctx),
                resolve(cfg.yawWeight, ctx),
                resolve(cfg.extraYawWeight, ctx),
                resolve(cfg.pitchWeight, ctx),
                resolve(cfg.extraPitchWeight, ctx),
                resolve(cfg.heightDelta, ctx),
                resolve(cfg.extraHeightDelta, ctx),
                resolve(cfg.horizontalCombine, ctx),
                resolve(cfg.verticalCombine, ctx),
                resolve(cfg.degenerateFallback, ctx),
                resolve(cfg.frictionH, ctx),
                resolve(cfg.frictionV, ctx),
                resolve(cfg.frictionExtraH, ctx),
                resolve(cfg.frictionExtraV, ctx),
                resolve(cfg.useAbsFrictionH, ctx),
                resolve(cfg.useAbsFrictionV, ctx),
                resolve(cfg.useAbsFrictionEH, ctx),
                resolve(cfg.useAbsFrictionEV, ctx),
                resolve(cfg.rangeStartH, ctx),
                resolve(cfg.rangeFactorH, ctx),
                resolve(cfg.rangeStartV, ctx),
                resolve(cfg.rangeFactorV, ctx),
                resolve(cfg.rangeStartExtraH, ctx),
                resolve(cfg.rangeFactorExtraH, ctx),
                resolve(cfg.rangeStartExtraV, ctx),
                resolve(cfg.rangeFactorExtraV, ctx),
                resolve(cfg.rangeMaxH, ctx),
                resolve(cfg.rangeMaxV, ctx),
                resolve(cfg.rangeMaxExtraH, ctx),
                resolve(cfg.rangeMaxExtraV, ctx),
                resolve(cfg.sweepFactorH, ctx),
                resolve(cfg.sweepFactorV, ctx),
                resolve(cfg.sweepFactorExtraH, ctx),
                resolve(cfg.sweepFactorExtraV, ctx),
                resolve(cfg.knockbackFormula, ctx),
                resolve(cfg.velocityMethod, ctx),
                resolve(cfg.velocityModeH, ctx),
                resolve(cfg.velocityModeV, ctx),
                resolve(cfg.velocityModeExtraH, ctx),
                resolve(cfg.velocityModeExtraV, ctx)
        );
    }

    private static <T> T resolve(@Nullable KnockbackConfig.FieldValue<T> fv, KnockbackContext ctx) {
        return fv != null ? fv.resolve(ctx) : null;
    }

    /** Resolved config with plain values. Used by KnockbackCalculator. */
    public record ResolvedKnockbackConfig(
            @Nullable Integer kbInvulnTicks,
            @Nullable Integer sprintBuffer,
            @Nullable Double horizontal,
            @Nullable Double vertical,
            @Nullable Double extraHorizontal,
            @Nullable Double extraVertical,
            @Nullable KnockbackConfig.Bounds horizontalBounds,
            @Nullable KnockbackConfig.Bounds verticalBounds,
            @Nullable KnockbackConfig.Bounds extraHorizontalBounds,
            @Nullable KnockbackConfig.Bounds extraVerticalBounds,
            @Nullable Double yawWeight,
            @Nullable Double extraYawWeight,
            @Nullable Double pitchWeight,
            @Nullable Double extraPitchWeight,
            @Nullable Double heightDelta,
            @Nullable Double extraHeightDelta,
            @Nullable KnockbackConfig.DirectionMode horizontalCombine,
            @Nullable KnockbackConfig.DirectionMode verticalCombine,
            @Nullable KnockbackConfig.DegenerateFallback degenerateFallback,
            @Nullable Double frictionH,
            @Nullable Double frictionV,
            @Nullable Double frictionExtraH,
            @Nullable Double frictionExtraV,
            @Nullable Boolean useAbsFrictionH,
            @Nullable Boolean useAbsFrictionV,
            @Nullable Boolean useAbsFrictionEH,
            @Nullable Boolean useAbsFrictionEV,
            @Nullable Double rangeStartH,
            @Nullable Double rangeFactorH,
            @Nullable Double rangeStartV,
            @Nullable Double rangeFactorV,
            @Nullable Double rangeStartExtraH,
            @Nullable Double rangeFactorExtraH,
            @Nullable Double rangeStartExtraV,
            @Nullable Double rangeFactorExtraV,
            @Nullable Double rangeMaxH,
            @Nullable Double rangeMaxV,
            @Nullable Double rangeMaxExtraH,
            @Nullable Double rangeMaxExtraV,
            @Nullable Double sweepFactorH,
            @Nullable Double sweepFactorV,
            @Nullable Double sweepFactorExtraH,
            @Nullable Double sweepFactorExtraV,
            @Nullable KnockbackConfig.KnockbackFormula knockbackFormula,
            @Nullable VelocityRule velocityMethod,
            @Nullable VelocityRule velocityModeH,
            @Nullable VelocityRule velocityModeV,
            @Nullable VelocityRule velocityModeExtraH,
            @Nullable VelocityRule velocityModeExtraV
    ) {}
}
