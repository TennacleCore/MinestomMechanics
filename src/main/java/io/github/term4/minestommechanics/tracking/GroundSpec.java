package io.github.term4.minestommechanics.tracking;

import org.jetbrains.annotations.Nullable;

/**
 * Knobs for the {@link GroundRule#collision(GroundSpec) collision} ground probe - the server-side, vanilla
 * {@code Entity.move()}-style ground test that sweeps the victim's bounding box down by its <em>simulated</em> fall
 * (vanilla's {@code deltaMovement.y}, deepened by the reported position delta) and reports whether a block clamps
 * that descent. Reads the server's own position + world geometry, so it stays correct while a laggy client's
 * {@code onGround} packet is still in flight. Build with {@link #builder()}.
 *
 * @param floor   minimum downward probe (blocks/tick) so a resting victim (fall ~0) still registers the block
 *                beneath it; {@code null} -> one gravity step ({@code gravity * verticalAirResistance}, vanilla
 *                {@code ~0.0784}). This is what makes a standing target read as grounded.
 * @param margin  extra downward probe (blocks/tick) added past the simulated fall - flat leniency on top of vanilla's
 *                velocity-driven reach, for laggier targets. {@code 0} = exactly vanilla (fall velocity only).
 * @param physics constants backing the {@link #floor} default, or {@code null} to use the entity's live physics.
 */
public record GroundSpec(@Nullable Double floor, double margin, @Nullable Physics physics) {

    /** Vanilla-faithful defaults: probe by the victim's fall velocity, floored at one gravity step, no extra margin. */
    public static GroundSpec defaults() { return builder().build(); }

    public Builder toBuilder() { return new Builder(this); }
    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private @Nullable Double floor;
        private double margin = 0.0;
        private @Nullable Physics physics;

        Builder() {}

        Builder(GroundSpec s) {
            floor = s.floor;
            margin = s.margin;
            physics = s.physics;
        }

        /** Minimum downward probe (blocks/tick); unset -> one gravity step, so a standing victim still registers. */
        public Builder floor(double v) { floor = v; return this; }

        /** Extra downward probe (blocks/tick) past the fall velocity - leniency for laggier targets ({@code 0} = vanilla). */
        public Builder margin(double v) { margin = v; return this; }

        /** Constant override backing the {@link #floor} default, or null to use the entity's live physics. */
        public Builder physics(@Nullable Physics v) { physics = v; return this; }

        public GroundSpec build() {
            return new GroundSpec(floor, margin, physics);
        }
    }
}
