package io.github.term4.minestommechanics.mechanics.knockback;

import io.github.term4.minestommechanics.tracking.VelocityRule;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** Immutable knockback config. Use {@link #builder()}, {@link #toBuilder()}. */
public final class KnockbackConfig {

    public enum DegenerateFallback { LOOK, RANDOM }
    public enum DirectionMode { SCALAR, VECTOR_ADDITION }
    public enum KnockbackFormula { CLASSIC, MODERN }
    /** How a friction value is applied to the reconstructed victim velocity in the friction term. */
    public enum FrictionMode {
        /** {@code mot / value} - the value is a divisor (default; vanilla-style). */
        DIVISOR,
        /** {@code mot * value} - the value is a multiplier, so coefficients (incl. negatives) read directly. */
        FACTOR
    }

    /** Lower and upper bound for knockback components. Null means no bound. */
    public record Bounds(@Nullable Double lower, @Nullable Double upper) {
        public static Bounds of(@Nullable Double lower, @Nullable Double upper) {
            return new Bounds(lower, upper);
        }
    }

    public record FieldValue<T>(Function<KnockbackConfigResolver.KnockbackContext, T> fn) {
        static <T> FieldValue<T> constant(T v) { return new FieldValue<>(ctx -> v); }
        static <T> FieldValue<T> of(Function<KnockbackConfigResolver.KnockbackContext, T> f) { return new FieldValue<>(f); }
        static <T> FieldValue<T> ofWithFallback(T fallback, Function<KnockbackConfigResolver.KnockbackContext, T> fn) {
            return new FieldValue<>(ctx -> { T r = fn.apply(ctx); return r != null ? r : fallback; });
        }
        T resolve(KnockbackConfigResolver.KnockbackContext ctx) { return fn.apply(ctx); }
        FieldValue<T> or(FieldValue<T> fallback) {
            return new FieldValue<>(ctx -> { T r = fn.apply(ctx); return r != null ? r : fallback.fn.apply(ctx); });
        }
    }

    private static <T> FieldValue<T> merge(@Nullable FieldValue<T> a, @Nullable FieldValue<T> b) {
        if (b == null) return a;
        if (a == null) return b;
        return a.or(b);
    }

    @Nullable public final Function<KnockbackConfigResolver.KnockbackContext, KnockbackConfig> subConfig;

    public final FieldValue<Integer> kbInvulTicks;
    public final FieldValue<Integer> sprintBuffer;
    public final FieldValue<Double> horizontal;
    public final FieldValue<Double> vertical;
    public final FieldValue<Double> extraHorizontal;
    public final FieldValue<Double> extraVertical;
    public final FieldValue<Bounds> horizontalBounds;
    public final FieldValue<Bounds> verticalBounds;
    public final FieldValue<Bounds> extraHorizontalBounds;
    public final FieldValue<Bounds> extraVerticalBounds;
    public final FieldValue<Double> yawWeight;
    public final FieldValue<Double> extraYawWeight;
    public final FieldValue<Double> pitchWeight;
    public final FieldValue<Double> extraPitchWeight;
    public final FieldValue<Double> heightDelta;
    public final FieldValue<Double> extraHeightDelta;
    public final FieldValue<DirectionMode> horizontalCombine;
    public final FieldValue<DirectionMode> verticalCombine;
    public final FieldValue<DegenerateFallback> degenerateFallback;
    public final FieldValue<Double> frictionH;
    public final FieldValue<Double> frictionV;
    public final FieldValue<FrictionMode> frictionModeH;
    public final FieldValue<FrictionMode> frictionModeV;
    public final FieldValue<Double> rangeStartH;
    public final FieldValue<Double> rangeFactorH;
    public final FieldValue<Double> rangeStartV;
    public final FieldValue<Double> rangeFactorV;
    public final FieldValue<Double> rangeStartExtraH;
    public final FieldValue<Double> rangeFactorExtraH;
    public final FieldValue<Double> rangeStartExtraV;
    public final FieldValue<Double> rangeFactorExtraV;
    public final FieldValue<Double> rangeMaxH;
    public final FieldValue<Double> rangeMaxV;
    public final FieldValue<Double> rangeMaxExtraH;
    public final FieldValue<Double> rangeMaxExtraV;
    public final FieldValue<Double> sweepFactorH;
    public final FieldValue<Double> sweepFactorV;
    public final FieldValue<Double> sweepFactorExtraH;
    public final FieldValue<Double> sweepFactorExtraV;
    public final FieldValue<KnockbackFormula> knockbackFormula;
    /** How the friction term reconstructs the victim's velocity (see {@link VelocityRule}). */
    public final FieldValue<VelocityRule> velocity;
    /**
     * Vertical launch cap-hold (blocks/tick). While the friction term's vertical velocity (see {@link #velocity}) is
     * still above this threshold - rising or only just starting to fall - vertical knockback is pinned to its
     * {@link #verticalBounds} upper bound instead of being sagged by the friction term. Once the fall builds past it,
     * the normal {@code base + v/frictionV} decay resumes. This is what makes a jump's cap hold longer than a
     * walk-off's (the rise keeps velocity above the threshold). {@code null} disables it (vanilla/Hypixel).
     */
    public final FieldValue<Double> verticalLaunchHold;
    /**
     * Extra additive terms summed onto the <em>final</em> knockback vector (blocks/tick, i.e. the client-decoded
     * packet units), after all base/extra/friction/range/bounds logic. Each {@link KnockbackComponent} self-gates
     * and may apply non-linear logic the base pipeline cannot (e.g. axis snapping). Applied in order; {@code null}
     * or empty means none. See {@link KnockbackComponent}.
     */
    @Nullable public final List<KnockbackComponent> customComponents;

    private KnockbackConfig(Builder b) {
        subConfig = b.subConfig;
        kbInvulTicks = b.kbInvulnTicks;
        sprintBuffer = b.sprintBuffer;
        horizontal = b.horizontal;
        vertical = b.vertical;
        extraHorizontal = b.extraHorizontal;
        extraVertical = b.extraVertical;
        horizontalBounds = b.horizontalBounds;
        verticalBounds = b.verticalBounds;
        extraHorizontalBounds = b.extraHorizontalBounds;
        extraVerticalBounds = b.extraVerticalBounds;
        yawWeight = b.yawWeight;
        extraYawWeight = b.extraYawWeight;
        pitchWeight = b.pitchWeight;
        extraPitchWeight = b.extraPitchWeight;
        heightDelta = b.heightDelta;
        extraHeightDelta = b.extraHeightDelta;
        horizontalCombine = b.horizontalCombine;
        verticalCombine = b.verticalCombine;
        degenerateFallback = b.degenerateFallback;
        frictionH = b.frictionH;
        frictionV = b.frictionV;
        frictionModeH = b.frictionModeH;
        frictionModeV = b.frictionModeV;
        rangeStartH = b.rangeStartH;
        rangeFactorH = b.rangeFactorH;
        rangeStartV = b.rangeStartV;
        rangeFactorV = b.rangeFactorV;
        rangeStartExtraH = b.rangeStartExtraH;
        rangeFactorExtraH = b.rangeFactorExtraH;
        rangeStartExtraV = b.rangeStartExtraV;
        rangeFactorExtraV = b.rangeFactorExtraV;
        rangeMaxH = b.rangeMaxH;
        rangeMaxV = b.rangeMaxV;
        rangeMaxExtraH = b.rangeMaxExtraH;
        rangeMaxExtraV = b.rangeMaxExtraV;
        sweepFactorH = b.sweepFactorH;
        sweepFactorV = b.sweepFactorV;
        sweepFactorExtraH = b.sweepFactorExtraH;
        sweepFactorExtraV = b.sweepFactorExtraV;
        knockbackFormula = b.knockbackFormula;
        velocity = b.velocity;
        verticalLaunchHold = b.verticalLaunchHold;
        customComponents = b.customComponents;
    }

    /** Merges this config over base. */
    public KnockbackConfig fromBase(KnockbackConfig base) {
        return new Builder()
                .subConfig(subConfig != null ? subConfig : base.subConfig)
                .kbInvulnTicks(merge(kbInvulTicks, base.kbInvulTicks))
                .sprintBuffer(merge(sprintBuffer, base.sprintBuffer))
                .horizontal(merge(horizontal, base.horizontal))
                .vertical(merge(vertical, base.vertical))
                .extraHorizontal(merge(extraHorizontal, base.extraHorizontal))
                .extraVertical(merge(extraVertical, base.extraVertical))
                .horizontalBounds(merge(horizontalBounds, base.horizontalBounds))
                .verticalBounds(merge(verticalBounds, base.verticalBounds))
                .extraHorizontalBounds(merge(extraHorizontalBounds, base.extraHorizontalBounds))
                .extraVerticalBounds(merge(extraVerticalBounds, base.extraVerticalBounds))
                .yawWeight(merge(yawWeight, base.yawWeight))
                .extraYawWeight(merge(extraYawWeight, base.extraYawWeight))
                .pitchWeight(merge(pitchWeight, base.pitchWeight))
                .extraPitchWeight(merge(extraPitchWeight, base.extraPitchWeight))
                .heightDelta(merge(heightDelta, base.heightDelta))
                .extraHeightDelta(merge(extraHeightDelta, base.extraHeightDelta))
                .horizontalCombine(merge(horizontalCombine, base.horizontalCombine))
                .verticalCombine(merge(verticalCombine, base.verticalCombine))
                .degenerateFallback(merge(degenerateFallback, base.degenerateFallback))
                .frictionH(merge(frictionH, base.frictionH))
                .frictionV(merge(frictionV, base.frictionV))
                .frictionModeH(merge(frictionModeH, base.frictionModeH))
                .frictionModeV(merge(frictionModeV, base.frictionModeV))
                .rangeStartH(merge(rangeStartH, base.rangeStartH))
                .rangeFactorH(merge(rangeFactorH, base.rangeFactorH))
                .rangeStartV(merge(rangeStartV, base.rangeStartV))
                .rangeFactorV(merge(rangeFactorV, base.rangeFactorV))
                .rangeStartExtraH(merge(rangeStartExtraH, base.rangeStartExtraH))
                .rangeFactorExtraH(merge(rangeFactorExtraH, base.rangeFactorExtraH))
                .rangeStartExtraV(merge(rangeStartExtraV, base.rangeStartExtraV))
                .rangeFactorExtraV(merge(rangeFactorExtraV, base.rangeFactorExtraV))
                .rangeMaxH(merge(rangeMaxH, base.rangeMaxH))
                .rangeMaxV(merge(rangeMaxV, base.rangeMaxV))
                .rangeMaxExtraH(merge(rangeMaxExtraH, base.rangeMaxExtraH))
                .rangeMaxExtraV(merge(rangeMaxExtraV, base.rangeMaxExtraV))
                .sweepFactorH(merge(sweepFactorH, base.sweepFactorH))
                .sweepFactorV(merge(sweepFactorV, base.sweepFactorV))
                .sweepFactorExtraH(merge(sweepFactorExtraH, base.sweepFactorExtraH))
                .sweepFactorExtraV(merge(sweepFactorExtraV, base.sweepFactorExtraV))
                .knockbackFormula(merge(knockbackFormula, base.knockbackFormula))
                .velocity(merge(velocity, base.velocity))
                .verticalLaunchHold(merge(verticalLaunchHold, base.verticalLaunchHold))
                .customComponents(customComponents != null ? customComponents : base.customComponents)
                .build();
    }

    /** Returns a copy of the current knockback config. */
    public KnockbackConfig copy() {
        return toBuilder().build();
    }

    public Builder toBuilder() {
        return new Builder(this);
    }

    public static Builder builder() {
        return builder(null);
    }

    public static Builder builder(@Nullable KnockbackConfig base) {
        return base != null ? new Builder(base) : new Builder();
    }

    /** Returns a new config with all nulls (use fromBase to fill from defaults). */
    public static KnockbackConfig empty() {
        return builder().build();
    }

    public static final class Builder {
        private Function<KnockbackConfigResolver.KnockbackContext, KnockbackConfig> subConfig;
        private FieldValue<Integer> kbInvulnTicks;
        private FieldValue<Integer> sprintBuffer;
        private FieldValue<Double> horizontal;
        private FieldValue<Double> vertical;
        private FieldValue<Double> extraHorizontal;
        private FieldValue<Double> extraVertical;
        private FieldValue<Bounds> horizontalBounds;
        private FieldValue<Bounds> verticalBounds;
        private FieldValue<Bounds> extraHorizontalBounds;
        private FieldValue<Bounds> extraVerticalBounds;
        private FieldValue<Double> yawWeight;
        private FieldValue<Double> extraYawWeight;
        private FieldValue<Double> pitchWeight;
        private FieldValue<Double> extraPitchWeight;
        private FieldValue<Double> heightDelta;
        private FieldValue<Double> extraHeightDelta;
        private FieldValue<DirectionMode> horizontalCombine;
        private FieldValue<DirectionMode> verticalCombine;
        private FieldValue<DegenerateFallback> degenerateFallback;
        private FieldValue<Double> frictionH;
        private FieldValue<Double> frictionV;
        private FieldValue<FrictionMode> frictionModeH;
        private FieldValue<FrictionMode> frictionModeV;
        private FieldValue<Double> rangeStartH;
        private FieldValue<Double> rangeFactorH;
        private FieldValue<Double> rangeStartV;
        private FieldValue<Double> rangeFactorV;
        private FieldValue<Double> rangeStartExtraH;
        private FieldValue<Double> rangeFactorExtraH;
        private FieldValue<Double> rangeStartExtraV;
        private FieldValue<Double> rangeFactorExtraV;
        private FieldValue<Double> rangeMaxH;
        private FieldValue<Double> rangeMaxV;
        private FieldValue<Double> rangeMaxExtraH;
        private FieldValue<Double> rangeMaxExtraV;
        private FieldValue<Double> sweepFactorH;
        private FieldValue<Double> sweepFactorV;
        private FieldValue<Double> sweepFactorExtraH;
        private FieldValue<Double> sweepFactorExtraV;
        private FieldValue<KnockbackFormula> knockbackFormula;
        private FieldValue<VelocityRule> velocity;
        private FieldValue<Double> verticalLaunchHold;
        private List<KnockbackComponent> customComponents;

        Builder() {}

        Builder(KnockbackConfig c) {
            subConfig = c.subConfig;
            kbInvulnTicks = c.kbInvulTicks;
            sprintBuffer = c.sprintBuffer;
            horizontal = c.horizontal;
            vertical = c.vertical;
            extraHorizontal = c.extraHorizontal;
            extraVertical = c.extraVertical;
            horizontalBounds = c.horizontalBounds;
            verticalBounds = c.verticalBounds;
            extraHorizontalBounds = c.extraHorizontalBounds;
            extraVerticalBounds = c.extraVerticalBounds;
            yawWeight = c.yawWeight;
            extraYawWeight = c.extraYawWeight;
            pitchWeight = c.pitchWeight;
            extraPitchWeight = c.extraPitchWeight;
            heightDelta = c.heightDelta;
            extraHeightDelta = c.extraHeightDelta;
            horizontalCombine = c.horizontalCombine;
            verticalCombine = c.verticalCombine;
            degenerateFallback = c.degenerateFallback;
            frictionH = c.frictionH;
            frictionV = c.frictionV;
            frictionModeH = c.frictionModeH;
            frictionModeV = c.frictionModeV;
            rangeStartH = c.rangeStartH;
            rangeFactorH = c.rangeFactorH;
            rangeStartV = c.rangeStartV;
            rangeFactorV = c.rangeFactorV;
            rangeStartExtraH = c.rangeStartExtraH;
            rangeFactorExtraH = c.rangeFactorExtraH;
            rangeStartExtraV = c.rangeStartExtraV;
            rangeFactorExtraV = c.rangeFactorExtraV;
            rangeMaxH = c.rangeMaxH;
            rangeMaxV = c.rangeMaxV;
            rangeMaxExtraH = c.rangeMaxExtraH;
            rangeMaxExtraV = c.rangeMaxExtraV;
            sweepFactorH = c.sweepFactorH;
            sweepFactorV = c.sweepFactorV;
            sweepFactorExtraH = c.sweepFactorExtraH;
            sweepFactorExtraV = c.sweepFactorExtraV;
            knockbackFormula = c.knockbackFormula;
            velocity = c.velocity;
            verticalLaunchHold = c.verticalLaunchHold;
            customComponents = c.customComponents;
        }

        public Builder subConfig(Function<KnockbackConfigResolver.KnockbackContext, KnockbackConfig> fn) { subConfig = fn; return this; }
        public Builder kbInvulnTicks(Integer v) { kbInvulnTicks = FieldValue.constant(v); return this; }
        public Builder kbInvulnTicks(Function<KnockbackConfigResolver.KnockbackContext, Integer> fn) { kbInvulnTicks = FieldValue.of(fn); return this; }
        public Builder kbInvulnTicks(Integer fallback, Function<KnockbackConfigResolver.KnockbackContext, Integer> fn) { kbInvulnTicks = FieldValue.ofWithFallback(fallback, fn); return this; }
        public Builder sprintBuffer(Integer v) { sprintBuffer = FieldValue.constant(v); return this; }
        public Builder sprintBuffer(Function<KnockbackConfigResolver.KnockbackContext, Integer> fn) { sprintBuffer = FieldValue.of(fn); return this; }
        public Builder sprintBuffer(Integer fallback, Function<KnockbackConfigResolver.KnockbackContext, Integer> fn) { sprintBuffer = FieldValue.ofWithFallback(fallback, fn); return this; }
        public Builder horizontal(Double v) { horizontal = FieldValue.constant(v); return this; }
        public Builder horizontal(Function<KnockbackConfigResolver.KnockbackContext, Double> fn) { horizontal = FieldValue.of(fn); return this; }
        public Builder horizontal(Double fallback, Function<KnockbackConfigResolver.KnockbackContext, Double> fn) { horizontal = FieldValue.ofWithFallback(fallback, fn); return this; }
        public Builder vertical(Double v) { vertical = FieldValue.constant(v); return this; }
        public Builder vertical(Function<KnockbackConfigResolver.KnockbackContext, Double> fn) { vertical = FieldValue.of(fn); return this; }
        public Builder vertical(Double fallback, Function<KnockbackConfigResolver.KnockbackContext, Double> fn) { vertical = FieldValue.ofWithFallback(fallback, fn); return this; }
        public Builder extraHorizontal(Double v) { extraHorizontal = FieldValue.constant(v); return this; }
        public Builder extraHorizontal(Function<KnockbackConfigResolver.KnockbackContext, Double> fn) { extraHorizontal = FieldValue.of(fn); return this; }
        public Builder extraHorizontal(Double fallback, Function<KnockbackConfigResolver.KnockbackContext, Double> fn) { extraHorizontal = FieldValue.ofWithFallback(fallback, fn); return this; }
        public Builder extraVertical(Double v) { extraVertical = FieldValue.constant(v); return this; }
        public Builder extraVertical(Function<KnockbackConfigResolver.KnockbackContext, Double> fn) { extraVertical = FieldValue.of(fn); return this; }
        public Builder extraVertical(Double fallback, Function<KnockbackConfigResolver.KnockbackContext, Double> fn) { extraVertical = FieldValue.ofWithFallback(fallback, fn); return this; }
        public Builder horizontalBounds(@Nullable Double lower, @Nullable Double upper) { horizontalBounds = FieldValue.constant(Bounds.of(lower, upper)); return this; }
        public Builder horizontalBounds(Bounds v) { horizontalBounds = FieldValue.constant(v); return this; }
        public Builder horizontalBounds(Function<KnockbackConfigResolver.KnockbackContext, Bounds> fn) { horizontalBounds = FieldValue.of(fn); return this; }
        public Builder horizontalBounds(Bounds fallback, Function<KnockbackConfigResolver.KnockbackContext, Bounds> fn) { horizontalBounds = FieldValue.ofWithFallback(fallback, fn); return this; }
        public Builder verticalBounds(@Nullable Double lower, @Nullable Double upper) { verticalBounds = FieldValue.constant(Bounds.of(lower, upper)); return this; }
        public Builder verticalBounds(Bounds v) { verticalBounds = FieldValue.constant(v); return this; }
        public Builder verticalBounds(Function<KnockbackConfigResolver.KnockbackContext, Bounds> fn) { verticalBounds = FieldValue.of(fn); return this; }
        public Builder verticalBounds(Bounds fallback, Function<KnockbackConfigResolver.KnockbackContext, Bounds> fn) { verticalBounds = FieldValue.ofWithFallback(fallback, fn); return this; }
        public Builder extraHorizontalBounds(@Nullable Double lower, @Nullable Double upper) { extraHorizontalBounds = FieldValue.constant(Bounds.of(lower, upper)); return this; }
        public Builder extraHorizontalBounds(Bounds v) { extraHorizontalBounds = FieldValue.constant(v); return this; }
        public Builder extraHorizontalBounds(Function<KnockbackConfigResolver.KnockbackContext, Bounds> fn) { extraHorizontalBounds = FieldValue.of(fn); return this; }
        public Builder extraHorizontalBounds(Bounds fallback, Function<KnockbackConfigResolver.KnockbackContext, Bounds> fn) { extraHorizontalBounds = FieldValue.ofWithFallback(fallback, fn); return this; }
        public Builder extraVerticalBounds(@Nullable Double lower, @Nullable Double upper) { extraVerticalBounds = FieldValue.constant(Bounds.of(lower, upper)); return this; }
        public Builder extraVerticalBounds(Bounds v) { extraVerticalBounds = FieldValue.constant(v); return this; }
        public Builder extraVerticalBounds(Function<KnockbackConfigResolver.KnockbackContext, Bounds> fn) { extraVerticalBounds = FieldValue.of(fn); return this; }
        public Builder extraVerticalBounds(Bounds fallback, Function<KnockbackConfigResolver.KnockbackContext, Bounds> fn) { extraVerticalBounds = FieldValue.ofWithFallback(fallback, fn); return this; }
        public Builder yawWeight(Double v) { yawWeight = FieldValue.constant(v); return this; }
        public Builder yawWeight(Function<KnockbackConfigResolver.KnockbackContext, Double> fn) { yawWeight = FieldValue.of(fn); return this; }
        public Builder yawWeight(Double fallback, Function<KnockbackConfigResolver.KnockbackContext, Double> fn) { yawWeight = FieldValue.ofWithFallback(fallback, fn); return this; }
        public Builder extraYawWeight(Double v) { extraYawWeight = FieldValue.constant(v); return this; }
        public Builder extraYawWeight(Function<KnockbackConfigResolver.KnockbackContext, Double> fn) { extraYawWeight = FieldValue.of(fn); return this; }
        public Builder extraYawWeight(Double fallback, Function<KnockbackConfigResolver.KnockbackContext, Double> fn) { extraYawWeight = FieldValue.ofWithFallback(fallback, fn); return this; }
        public Builder pitchWeight(Double v) { pitchWeight = FieldValue.constant(v); return this; }
        public Builder pitchWeight(Function<KnockbackConfigResolver.KnockbackContext, Double> fn) { pitchWeight = FieldValue.of(fn); return this; }
        public Builder pitchWeight(Double fallback, Function<KnockbackConfigResolver.KnockbackContext, Double> fn) { pitchWeight = FieldValue.ofWithFallback(fallback, fn); return this; }
        public Builder extraPitchWeight(Double v) { extraPitchWeight = FieldValue.constant(v); return this; }
        public Builder extraPitchWeight(Function<KnockbackConfigResolver.KnockbackContext, Double> fn) { extraPitchWeight = FieldValue.of(fn); return this; }
        public Builder extraPitchWeight(Double fallback, Function<KnockbackConfigResolver.KnockbackContext, Double> fn) { extraPitchWeight = FieldValue.ofWithFallback(fallback, fn); return this; }
        public Builder heightDelta(Double v) { heightDelta = FieldValue.constant(v); return this; }
        public Builder heightDelta(Function<KnockbackConfigResolver.KnockbackContext, Double> fn) { heightDelta = FieldValue.of(fn); return this; }
        public Builder heightDelta(Double fallback, Function<KnockbackConfigResolver.KnockbackContext, Double> fn) { heightDelta = FieldValue.ofWithFallback(fallback, fn); return this; }
        public Builder extraHeightDelta(Double v) { extraHeightDelta = FieldValue.constant(v); return this; }
        public Builder extraHeightDelta(Function<KnockbackConfigResolver.KnockbackContext, Double> fn) { extraHeightDelta = FieldValue.of(fn); return this; }
        public Builder extraHeightDelta(Double fallback, Function<KnockbackConfigResolver.KnockbackContext, Double> fn) { extraHeightDelta = FieldValue.ofWithFallback(fallback, fn); return this; }
        public Builder horizontalCombine(DirectionMode v) { horizontalCombine = FieldValue.constant(v); return this; }
        public Builder horizontalCombine(Function<KnockbackConfigResolver.KnockbackContext, DirectionMode> fn) { horizontalCombine = FieldValue.of(fn); return this; }
        public Builder horizontalCombine(DirectionMode fallback, Function<KnockbackConfigResolver.KnockbackContext, DirectionMode> fn) { horizontalCombine = FieldValue.ofWithFallback(fallback, fn); return this; }
        public Builder verticalCombine(DirectionMode v) { verticalCombine = FieldValue.constant(v); return this; }
        public Builder verticalCombine(Function<KnockbackConfigResolver.KnockbackContext, DirectionMode> fn) { verticalCombine = FieldValue.of(fn); return this; }
        public Builder verticalCombine(DirectionMode fallback, Function<KnockbackConfigResolver.KnockbackContext, DirectionMode> fn) { verticalCombine = FieldValue.ofWithFallback(fallback, fn); return this; }
        public Builder degenerateFallback(DegenerateFallback v) { degenerateFallback = FieldValue.constant(v); return this; }
        public Builder degenerateFallback(Function<KnockbackConfigResolver.KnockbackContext, DegenerateFallback> fn) { degenerateFallback = FieldValue.of(fn); return this; }
        public Builder degenerateFallback(DegenerateFallback fallback, Function<KnockbackConfigResolver.KnockbackContext, DegenerateFallback> fn) { degenerateFallback = FieldValue.ofWithFallback(fallback, fn); return this; }
        public Builder frictionH(Double v) { frictionH = FieldValue.constant(v); return this; }
        public Builder frictionH(Function<KnockbackConfigResolver.KnockbackContext, Double> fn) { frictionH = FieldValue.of(fn); return this; }
        public Builder frictionH(Double fallback, Function<KnockbackConfigResolver.KnockbackContext, Double> fn) { frictionH = FieldValue.ofWithFallback(fallback, fn); return this; }
        public Builder frictionV(Double v) { frictionV = FieldValue.constant(v); return this; }
        public Builder frictionV(Function<KnockbackConfigResolver.KnockbackContext, Double> fn) { frictionV = FieldValue.of(fn); return this; }
        public Builder frictionV(Double fallback, Function<KnockbackConfigResolver.KnockbackContext, Double> fn) { frictionV = FieldValue.ofWithFallback(fallback, fn); return this; }
        public Builder frictionModeH(FrictionMode v) { frictionModeH = FieldValue.constant(v); return this; }
        public Builder frictionModeH(Function<KnockbackConfigResolver.KnockbackContext, FrictionMode> fn) { frictionModeH = FieldValue.of(fn); return this; }
        public Builder frictionModeH(FrictionMode fallback, Function<KnockbackConfigResolver.KnockbackContext, FrictionMode> fn) { frictionModeH = FieldValue.ofWithFallback(fallback, fn); return this; }
        public Builder frictionModeV(FrictionMode v) { frictionModeV = FieldValue.constant(v); return this; }
        public Builder frictionModeV(Function<KnockbackConfigResolver.KnockbackContext, FrictionMode> fn) { frictionModeV = FieldValue.of(fn); return this; }
        public Builder frictionModeV(FrictionMode fallback, Function<KnockbackConfigResolver.KnockbackContext, FrictionMode> fn) { frictionModeV = FieldValue.ofWithFallback(fallback, fn); return this; }
        public Builder rangeStartH(Double v) { rangeStartH = FieldValue.constant(v); return this; }
        public Builder rangeStartH(Function<KnockbackConfigResolver.KnockbackContext, Double> fn) { rangeStartH = FieldValue.of(fn); return this; }
        public Builder rangeStartH(Double fallback, Function<KnockbackConfigResolver.KnockbackContext, Double> fn) { rangeStartH = FieldValue.ofWithFallback(fallback, fn); return this; }
        public Builder rangeFactorH(Double v) { rangeFactorH = FieldValue.constant(v); return this; }
        public Builder rangeFactorH(Function<KnockbackConfigResolver.KnockbackContext, Double> fn) { rangeFactorH = FieldValue.of(fn); return this; }
        public Builder rangeFactorH(Double fallback, Function<KnockbackConfigResolver.KnockbackContext, Double> fn) { rangeFactorH = FieldValue.ofWithFallback(fallback, fn); return this; }
        public Builder rangeStartV(Double v) { rangeStartV = FieldValue.constant(v); return this; }
        public Builder rangeStartV(Function<KnockbackConfigResolver.KnockbackContext, Double> fn) { rangeStartV = FieldValue.of(fn); return this; }
        public Builder rangeStartV(Double fallback, Function<KnockbackConfigResolver.KnockbackContext, Double> fn) { rangeStartV = FieldValue.ofWithFallback(fallback, fn); return this; }
        public Builder rangeFactorV(Double v) { rangeFactorV = FieldValue.constant(v); return this; }
        public Builder rangeFactorV(Function<KnockbackConfigResolver.KnockbackContext, Double> fn) { rangeFactorV = FieldValue.of(fn); return this; }
        public Builder rangeFactorV(Double fallback, Function<KnockbackConfigResolver.KnockbackContext, Double> fn) { rangeFactorV = FieldValue.ofWithFallback(fallback, fn); return this; }
        public Builder rangeStartExtraH(Double v) { rangeStartExtraH = FieldValue.constant(v); return this; }
        public Builder rangeStartExtraH(Function<KnockbackConfigResolver.KnockbackContext, Double> fn) { rangeStartExtraH = FieldValue.of(fn); return this; }
        public Builder rangeStartExtraH(Double fallback, Function<KnockbackConfigResolver.KnockbackContext, Double> fn) { rangeStartExtraH = FieldValue.ofWithFallback(fallback, fn); return this; }
        public Builder rangeFactorExtraH(Double v) { rangeFactorExtraH = FieldValue.constant(v); return this; }
        public Builder rangeFactorExtraH(Function<KnockbackConfigResolver.KnockbackContext, Double> fn) { rangeFactorExtraH = FieldValue.of(fn); return this; }
        public Builder rangeFactorExtraH(Double fallback, Function<KnockbackConfigResolver.KnockbackContext, Double> fn) { rangeFactorExtraH = FieldValue.ofWithFallback(fallback, fn); return this; }
        public Builder rangeStartExtraV(Double v) { rangeStartExtraV = FieldValue.constant(v); return this; }
        public Builder rangeStartExtraV(Function<KnockbackConfigResolver.KnockbackContext, Double> fn) { rangeStartExtraV = FieldValue.of(fn); return this; }
        public Builder rangeStartExtraV(Double fallback, Function<KnockbackConfigResolver.KnockbackContext, Double> fn) { rangeStartExtraV = FieldValue.ofWithFallback(fallback, fn); return this; }
        public Builder rangeFactorExtraV(Double v) { rangeFactorExtraV = FieldValue.constant(v); return this; }
        public Builder rangeFactorExtraV(Function<KnockbackConfigResolver.KnockbackContext, Double> fn) { rangeFactorExtraV = FieldValue.of(fn); return this; }
        public Builder rangeFactorExtraV(Double fallback, Function<KnockbackConfigResolver.KnockbackContext, Double> fn) { rangeFactorExtraV = FieldValue.ofWithFallback(fallback, fn); return this; }
        public Builder rangeMaxH(Double v) { rangeMaxH = FieldValue.constant(v); return this; }
        public Builder rangeMaxH(Function<KnockbackConfigResolver.KnockbackContext, Double> fn) { rangeMaxH = FieldValue.of(fn); return this; }
        public Builder rangeMaxH(Double fallback, Function<KnockbackConfigResolver.KnockbackContext, Double> fn) { rangeMaxH = FieldValue.ofWithFallback(fallback, fn); return this; }
        public Builder rangeMaxV(Double v) { rangeMaxV = FieldValue.constant(v); return this; }
        public Builder rangeMaxV(Function<KnockbackConfigResolver.KnockbackContext, Double> fn) { rangeMaxV = FieldValue.of(fn); return this; }
        public Builder rangeMaxV(Double fallback, Function<KnockbackConfigResolver.KnockbackContext, Double> fn) { rangeMaxV = FieldValue.ofWithFallback(fallback, fn); return this; }
        public Builder rangeMaxExtraH(Double v) { rangeMaxExtraH = FieldValue.constant(v); return this; }
        public Builder rangeMaxExtraH(Function<KnockbackConfigResolver.KnockbackContext, Double> fn) { rangeMaxExtraH = FieldValue.of(fn); return this; }
        public Builder rangeMaxExtraH(Double fallback, Function<KnockbackConfigResolver.KnockbackContext, Double> fn) { rangeMaxExtraH = FieldValue.ofWithFallback(fallback, fn); return this; }
        public Builder rangeMaxExtraV(Double v) { rangeMaxExtraV = FieldValue.constant(v); return this; }
        public Builder rangeMaxExtraV(Function<KnockbackConfigResolver.KnockbackContext, Double> fn) { rangeMaxExtraV = FieldValue.of(fn); return this; }
        public Builder rangeMaxExtraV(Double fallback, Function<KnockbackConfigResolver.KnockbackContext, Double> fn) { rangeMaxExtraV = FieldValue.ofWithFallback(fallback, fn); return this; }
        public Builder sweepFactorH(Double v) { sweepFactorH = FieldValue.constant(v); return this; }
        public Builder sweepFactorH(Function<KnockbackConfigResolver.KnockbackContext, Double> fn) { sweepFactorH = FieldValue.of(fn); return this; }
        public Builder sweepFactorH(Double fallback, Function<KnockbackConfigResolver.KnockbackContext, Double> fn) { sweepFactorH = FieldValue.ofWithFallback(fallback, fn); return this; }
        public Builder sweepFactorV(Double v) { sweepFactorV = FieldValue.constant(v); return this; }
        public Builder sweepFactorV(Function<KnockbackConfigResolver.KnockbackContext, Double> fn) { sweepFactorV = FieldValue.of(fn); return this; }
        public Builder sweepFactorV(Double fallback, Function<KnockbackConfigResolver.KnockbackContext, Double> fn) { sweepFactorV = FieldValue.ofWithFallback(fallback, fn); return this; }
        public Builder sweepFactorExtraH(Double v) { sweepFactorExtraH = FieldValue.constant(v); return this; }
        public Builder sweepFactorExtraH(Function<KnockbackConfigResolver.KnockbackContext, Double> fn) { sweepFactorExtraH = FieldValue.of(fn); return this; }
        public Builder sweepFactorExtraH(Double fallback, Function<KnockbackConfigResolver.KnockbackContext, Double> fn) { sweepFactorExtraH = FieldValue.ofWithFallback(fallback, fn); return this; }
        public Builder sweepFactorExtraV(Double v) { sweepFactorExtraV = FieldValue.constant(v); return this; }
        public Builder sweepFactorExtraV(Function<KnockbackConfigResolver.KnockbackContext, Double> fn) { sweepFactorExtraV = FieldValue.of(fn); return this; }
        public Builder sweepFactorExtraV(Double fallback, Function<KnockbackConfigResolver.KnockbackContext, Double> fn) { sweepFactorExtraV = FieldValue.ofWithFallback(fallback, fn); return this; }
        public Builder knockbackFormula(KnockbackFormula v) { knockbackFormula = FieldValue.constant(v); return this; }
        public Builder knockbackFormula(Function<KnockbackConfigResolver.KnockbackContext, KnockbackFormula> fn) { knockbackFormula = FieldValue.of(fn); return this; }
        public Builder knockbackFormula(KnockbackFormula fallback, Function<KnockbackConfigResolver.KnockbackContext, KnockbackFormula> fn) { knockbackFormula = FieldValue.ofWithFallback(fallback, fn); return this; }
        public Builder velocity(VelocityRule v) { velocity = FieldValue.constant(v); return this; }
        public Builder velocity(Function<KnockbackConfigResolver.KnockbackContext, VelocityRule> fn) { velocity = FieldValue.of(fn); return this; }
        public Builder velocity(VelocityRule fallback, Function<KnockbackConfigResolver.KnockbackContext, VelocityRule> fn) { velocity = FieldValue.ofWithFallback(fallback, fn); return this; }
        public Builder verticalLaunchHold(Double v) { verticalLaunchHold = FieldValue.constant(v); return this; }
        public Builder verticalLaunchHold(Function<KnockbackConfigResolver.KnockbackContext, Double> fn) { verticalLaunchHold = FieldValue.of(fn); return this; }
        public Builder verticalLaunchHold(Double fallback, Function<KnockbackConfigResolver.KnockbackContext, Double> fn) { verticalLaunchHold = FieldValue.ofWithFallback(fallback, fn); return this; }
        /** Appends to the current/inherited components (copying first, so shared lists aren't mutated). */
        public Builder addCustomComponent(KnockbackComponent component) {
            List<KnockbackComponent> list = customComponents == null ? new ArrayList<>() : new ArrayList<>(customComponents);
            list.add(component);
            customComponents = list;
            return this;
        }
        Builder kbInvulnTicks(FieldValue<Integer> v) { kbInvulnTicks = v; return this; }
        Builder sprintBuffer(FieldValue<Integer> v) { sprintBuffer = v; return this; }
        Builder horizontal(FieldValue<Double> v) { horizontal = v; return this; }
        Builder vertical(FieldValue<Double> v) { vertical = v; return this; }
        Builder extraHorizontal(FieldValue<Double> v) { extraHorizontal = v; return this; }
        Builder extraVertical(FieldValue<Double> v) { extraVertical = v; return this; }
        Builder horizontalBounds(FieldValue<Bounds> v) { horizontalBounds = v; return this; }
        Builder verticalBounds(FieldValue<Bounds> v) { verticalBounds = v; return this; }
        Builder extraHorizontalBounds(FieldValue<Bounds> v) { extraHorizontalBounds = v; return this; }
        Builder extraVerticalBounds(FieldValue<Bounds> v) { extraVerticalBounds = v; return this; }
        Builder yawWeight(FieldValue<Double> v) { yawWeight = v; return this; }
        Builder extraYawWeight(FieldValue<Double> v) { extraYawWeight = v; return this; }
        Builder pitchWeight(FieldValue<Double> v) { pitchWeight = v; return this; }
        Builder extraPitchWeight(FieldValue<Double> v) { extraPitchWeight = v; return this; }
        Builder heightDelta(FieldValue<Double> v) { heightDelta = v; return this; }
        Builder extraHeightDelta(FieldValue<Double> v) { extraHeightDelta = v; return this; }
        Builder horizontalCombine(FieldValue<DirectionMode> v) { horizontalCombine = v; return this; }
        Builder verticalCombine(FieldValue<DirectionMode> v) { verticalCombine = v; return this; }
        Builder degenerateFallback(FieldValue<DegenerateFallback> v) { degenerateFallback = v; return this; }
        Builder frictionH(FieldValue<Double> v) { frictionH = v; return this; }
        Builder frictionV(FieldValue<Double> v) { frictionV = v; return this; }
        Builder frictionModeH(FieldValue<FrictionMode> v) { frictionModeH = v; return this; }
        Builder frictionModeV(FieldValue<FrictionMode> v) { frictionModeV = v; return this; }
        Builder rangeStartH(FieldValue<Double> v) { rangeStartH = v; return this; }
        Builder rangeFactorH(FieldValue<Double> v) { rangeFactorH = v; return this; }
        Builder rangeStartV(FieldValue<Double> v) { rangeStartV = v; return this; }
        Builder rangeFactorV(FieldValue<Double> v) { rangeFactorV = v; return this; }
        Builder rangeStartExtraH(FieldValue<Double> v) { rangeStartExtraH = v; return this; }
        Builder rangeFactorExtraH(FieldValue<Double> v) { rangeFactorExtraH = v; return this; }
        Builder rangeStartExtraV(FieldValue<Double> v) { rangeStartExtraV = v; return this; }
        Builder rangeFactorExtraV(FieldValue<Double> v) { rangeFactorExtraV = v; return this; }
        Builder rangeMaxH(FieldValue<Double> v) { rangeMaxH = v; return this; }
        Builder rangeMaxV(FieldValue<Double> v) { rangeMaxV = v; return this; }
        Builder rangeMaxExtraH(FieldValue<Double> v) { rangeMaxExtraH = v; return this; }
        Builder rangeMaxExtraV(FieldValue<Double> v) { rangeMaxExtraV = v; return this; }
        Builder sweepFactorH(FieldValue<Double> v) { sweepFactorH = v; return this; }
        Builder sweepFactorV(FieldValue<Double> v) { sweepFactorV = v; return this; }
        Builder sweepFactorExtraH(FieldValue<Double> v) { sweepFactorExtraH = v; return this; }
        Builder sweepFactorExtraV(FieldValue<Double> v) { sweepFactorExtraV = v; return this; }
        Builder knockbackFormula(FieldValue<KnockbackFormula> v) { knockbackFormula = v; return this; }
        Builder velocity(FieldValue<VelocityRule> v) { velocity = v; return this; }
        Builder verticalLaunchHold(FieldValue<Double> v) { verticalLaunchHold = v; return this; }
        Builder customComponents(List<KnockbackComponent> v) { customComponents = v; return this; }

        public KnockbackConfig build() {
            return new KnockbackConfig(this);
        }
    }
}
