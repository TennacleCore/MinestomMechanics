package io.github.term4.minestommechanics;

import io.github.term4.minestommechanics.mechanics.attack.AttackConfig;
import io.github.term4.minestommechanics.mechanics.attack.rulesets.AttackProcessor;
import io.github.term4.minestommechanics.mechanics.knockback.KnockbackConfig;

/** Preconfigured vanilla 1.8 values. Use these as defaults when resolving nullable configs. */
public final class Vanilla18 {

    private Vanilla18() {}

    /** Returns an AttackConfig with vanilla 1.8-like values. Buffers disabled (0). */
    public static AttackConfig atk() {
        return AttackConfig.builder()
                .enabled(true)
                .packetHits(true)
                .swingHits(false)
                .sprintBuffer(0)
                .hitQueueBuffer(0)
                .packetReach(10.0)
                .swingReach(3.0)
                .packetPadding(2.0)
                .swingPadding(0.0)
                .ruleset(AttackProcessor.legacy())
                .build();
    }

    /** Returns a KnockbackConfig with all vanilla 1.8 values set (no nulls). */
    public static KnockbackConfig kb() {
        return KnockbackConfig.builder()
                .sprintBuffer(0)
                .horizontal(0.4)
                .vertical(0.4)
                .extraHorizontal(0.5)
                .extraVertical(0.1)
                .verticalBounds(null, 0.4000000059604645)
                .yawWeight(0.0)
                .extraYawWeight(1.0)
                .pitchWeight(0.0)
                .extraPitchWeight(0.0)
                .heightDelta(0.0)
                .extraHeightDelta(0.0)
                .horizontalCombine(KnockbackConfig.DirectionMode.VECTOR_ADDITION)
                .verticalCombine(KnockbackConfig.DirectionMode.SCALAR)
                .degenerateFallback(KnockbackConfig.DegenerateFallback.RANDOM)
                .frictionH(2.0)
                .frictionV(2.0)
                .frictionExtraH(2.0)
                .frictionExtraV(2.0)
                .rangeStartH(0.0)
                .rangeFactorH(0.0)
                .rangeStartV(0.0)
                .rangeFactorV(0.0)
                .rangeStartExtraH(0.0)
                .rangeFactorExtraH(0.0)
                .rangeStartExtraV(0.0)
                .rangeFactorExtraV(0.0)
                .rangeMaxH(0.0)
                .rangeMaxV(0.0)
                .rangeMaxExtraH(0.0)
                .rangeMaxExtraV(0.0)
                .sweepFactorH(0.0)
                .sweepFactorV(0.0)
                .sweepFactorExtraH(0.0)
                .sweepFactorExtraV(0.0)
                .knockbackFormula(KnockbackConfig.KnockbackFormula.CLASSIC)
                .velocityMethod(KnockbackConfig.VelocityMethod.LEGACY)
                .build();
    }
}
