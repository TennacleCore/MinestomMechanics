package io.github.term4.minestommechanics.tracking;

import org.jetbrains.annotations.Nullable;

/**
 * Knobs for the {@link VelocityRule#simulated(ArcSpec) simulated} arc: the air-tick {@link #launchOffset}, the
 * per-axis {@link #horizontalStyle}/{@link #verticalStyle}, the launch {@link #seed} override, the {@link #jumpBoost}
 * toggle, an optional {@link #physics} constant override (null -> the entity's own {@link Physics#fromEntity}), and
 * the {@link #groundRule} gating the air clock. Build with {@link #builder()}.
 *
 * @param launchOffset    air-tick correction (vanilla {@link VelocityRule#VANILLA_LAUNCH_OFFSET}).
 * @param horizontalStyle how the x/z arc is evaluated.
 * @param verticalStyle   how the y arc is evaluated.
 * @param seed            vertical takeoff velocity for the launch arc, or null to use {@link Physics#jumpVelocity()}.
 * @param jumpBoost       add the Jump Boost effect to the vertical seed. TODO: scaffolded, not yet applied.
 * @param physics         constant overrides, or null to use the entity's live physics.
 * @param groundRule      ground test gating the air clock - a grounded victim resets to the resting arc instead of a
 *                        stale descent; a per-entity {@link MotionTracker#setGroundRule override} still wins. Null ->
 *                        {@link GroundRule#DEFAULT} (vanilla {@link GroundRule#collision()}).
 */
public record ArcSpec(
        int launchOffset,
        VelocityRule.ArcStyle horizontalStyle,
        VelocityRule.ArcStyle verticalStyle,
        @Nullable Double seed,
        boolean jumpBoost,
        @Nullable Physics physics,
        @Nullable GroundRule groundRule
) {

    /**
     * Vanilla defaults: offset {@code -2}, per-tick both axes, entity-default seed, no jump boost, entity physics,
     * and vanilla {@link GroundRule#collision()} grounding.
     */
    public static ArcSpec defaults() { return builder().build(); }

    public Builder toBuilder() { return new Builder(this); }
    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private int launchOffset = VelocityRule.VANILLA_LAUNCH_OFFSET;
        private VelocityRule.ArcStyle horizontalStyle = VelocityRule.ArcStyle.PER_TICK;
        private VelocityRule.ArcStyle verticalStyle = VelocityRule.ArcStyle.PER_TICK;
        private @Nullable Double seed;
        private boolean jumpBoost = false;
        private @Nullable Physics physics;
        private @Nullable GroundRule groundRule = GroundRule.DEFAULT;

        Builder() {}

        Builder(ArcSpec s) {
            launchOffset = s.launchOffset;
            horizontalStyle = s.horizontalStyle;
            verticalStyle = s.verticalStyle;
            seed = s.seed;
            jumpBoost = s.jumpBoost;
            physics = s.physics;
            groundRule = s.groundRule;
        }

        public Builder launchOffset(int v) { launchOffset = v; return this; }
        public Builder horizontalStyle(VelocityRule.ArcStyle v) { horizontalStyle = v; return this; }
        public Builder verticalStyle(VelocityRule.ArcStyle v) { verticalStyle = v; return this; }
        public Builder style(VelocityRule.ArcStyle both) { horizontalStyle = both; verticalStyle = both; return this; }
        public Builder seed(double v) { seed = v; return this; }
        public Builder jumpBoost(boolean v) { jumpBoost = v; return this; }
        public Builder physics(@Nullable Physics v) { physics = v; return this; }

        /** Ground test gating the air clock; {@code null} -> {@link GroundRule#DEFAULT} (vanilla collision). */
        public Builder groundRule(@Nullable GroundRule v) { groundRule = v; return this; }

        public ArcSpec build() {
            return new ArcSpec(launchOffset, horizontalStyle, verticalStyle, seed, jumpBoost, physics, groundRule);
        }
    }
}
