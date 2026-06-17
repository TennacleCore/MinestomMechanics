package io.github.term4.minestommechanics.tracking.motion;

import org.jetbrains.annotations.Nullable;

/**
 * Knobs for the {@link VelocityRule#simulated(VelocityConfig) simulated} server-tracked velocity. Plain values
 * (per-context conditionality lives one level up). Gravity/drag/friction are read live from the entity, not here.
 * Build with {@link #builder()}.
 *
 * @param seed            fallback takeoff motY (vanilla {@link #JUMP_VELOCITY}); the ticked sim always uses {@link #JUMP_VELOCITY}.
 * @param launchOffset    arc phase correction ({@link #DEFAULT_LAUNCH_OFFSET}); debug knob.
 * @param clampX          near-zero clamp for the folded x ({@link #CLAMP}).
 * @param clampY          near-zero clamp for motY; {@code 0} disables (selects the sim's apex-reseed variant).
 * @param clampZ          near-zero clamp for the folded z.
 * @param groundTicks     fall-prediction depth for {@link MotionTracker#onGround} ({@code 0} = raw client flag).
 * @param maxAirTicks     fallback only: caps the air clock; {@code null} = unbounded.
 * @param entityPush      fold the {@code Entity.collide} push residual (disable where player collision is off).
 * @param fluidPhysics    built-in water/lava drag + buoyancy handling (default on; off also disables {@link #flowPush}).
 * @param climbPhysics    built-in ladder/vine/climbable handling (default on).
 * @param webPhysics      built-in cobweb handling - zeroes motion (default on).
 * @param flowPush        fold the water-flow current residual (simulated rules only).
 * @param flowModel       which {@link FluidFlow.Model} the flow residual uses (1.8 vs 26.1).
 * @param flowLava        whether MODERN flow also pushes in lava (26 yes; Hypixel no). No effect on LEGACY.
 * @param climbModel      which {@link ClimbModel} the climb handling uses (1.8 vs 26.1).
 * @param modernBlockPhysics 26-only block velocity (sweet-berry/powder-snow stuck + bed bounce); default off (1.8).
 */
public record VelocityConfig(
        double seed,
        int launchOffset,
        double clampX,
        double clampY,
        double clampZ,
        int groundTicks,
        @Nullable Integer maxAirTicks,
        boolean entityPush,
        boolean fluidPhysics,
        boolean climbPhysics,
        boolean webPhysics,
        boolean flowPush,
        FluidFlow.Model flowModel,
        boolean flowLava,
        ClimbModel climbModel,
        boolean modernBlockPhysics
) {

    /** Vanilla gravity (b/t^2): the {@code motY -= 0.08} step. */
    public static final double GRAVITY = 0.08;
    /** Vanilla vertical air drag: the {@code motY *= 0.98} step. */
    public static final double DRAG_V = 0.98;
    /** Vanilla horizontal air drag ({@code motX/motZ *= 0.91} airborne). */
    public static final double DRAG_H = 0.91;
    /** Vanilla jump takeoff velocity ({@code bF()} {@code 0.42F} widened to double); the default {@link #seed}. Float-exact for the hurt-broadcast wire short. */
    public static final double JUMP_VELOCITY = 0.41999998688697815;
    /** Vanilla near-zero motion clamp ({@code m()} zeroes {@code |mot| < 0.005} each tick). */
    public static final double CLAMP = 0.005;

    /** Air-tick phase correction ({@code -1}): the hit packet is processed one tick before the victim's move, so the fold reads {@code ticksInAir - 1}. */
    public static final int DEFAULT_LAUNCH_OFFSET = -1;

    /** Vanilla defaults (0.42 seed, -1 offset, 0.005 clamps, fluid/climb/web on, entity push + flow folded). */
    public static VelocityConfig defaults() { return builder().build(); }

    public Builder toBuilder() { return new Builder(this); }
    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private double seed = JUMP_VELOCITY;
        private int launchOffset = DEFAULT_LAUNCH_OFFSET;
        private double clampX = CLAMP;
        private double clampY = CLAMP;
        private double clampZ = CLAMP;
        private int groundTicks = 1;
        private @Nullable Integer maxAirTicks;
        private boolean entityPush = true;
        private boolean fluidPhysics = true;
        private boolean climbPhysics = true;
        private boolean webPhysics = true;
        private boolean flowPush = true;
        private FluidFlow.Model flowModel = FluidFlow.Model.LEGACY; // Vanilla(26) sets MODERN
        private boolean flowLava = true;    // MODERN only; Hypixel sets false
        private ClimbModel climbModel = ClimbModel.LEGACY; // Vanilla(26) sets MODERN
        private boolean modernBlockPhysics = false; // Vanilla(26) sets true

        Builder() {}

        Builder(VelocityConfig c) {
            seed = c.seed;
            launchOffset = c.launchOffset;
            clampX = c.clampX;
            clampY = c.clampY;
            clampZ = c.clampZ;
            groundTicks = c.groundTicks;
            maxAirTicks = c.maxAirTicks;
            entityPush = c.entityPush;
            fluidPhysics = c.fluidPhysics;
            climbPhysics = c.climbPhysics;
            webPhysics = c.webPhysics;
            flowPush = c.flowPush;
            flowModel = c.flowModel;
            flowLava = c.flowLava;
            climbModel = c.climbModel;
            modernBlockPhysics = c.modernBlockPhysics;
        }

        public Builder seed(double v) { seed = v; return this; }
        public Builder launchOffset(int v) { launchOffset = v; return this; }
        public Builder clamp(double all) { clampX = all; clampY = all; clampZ = all; return this; }
        public Builder clampX(double v) { clampX = v; return this; }
        public Builder clampY(double v) { clampY = v; return this; }
        public Builder clampZ(double v) { clampZ = v; return this; }
        public Builder groundTicks(int v) { groundTicks = v; return this; }
        public Builder maxAirTicks(@Nullable Integer v) { maxAirTicks = v; return this; }
        public Builder entityPush(boolean v) { entityPush = v; return this; }
        /** Enable/disable the tracker's built-in fluid (water/lava) reconstruction handling. See {@link VelocityConfig#fluidPhysics}. */
        public Builder fluidPhysics(boolean v) { fluidPhysics = v; return this; }
        /** Enable/disable the tracker's built-in climb (ladder/vine/climbable) reconstruction handling. See {@link VelocityConfig#climbPhysics}. */
        public Builder climbPhysics(boolean v) { climbPhysics = v; return this; }
        /** Enable/disable the tracker's built-in cobweb reconstruction handling. See {@link VelocityConfig#webPhysics}. */
        public Builder webPhysics(boolean v) { webPhysics = v; return this; }
        /** Enable/disable folding the water-flow residual (vanilla 1.8's {@code 0.014} current). See {@link VelocityConfig#flowPush}. */
        public Builder flowPush(boolean v) { flowPush = v; return this; }
        /** Select the fluid-flow model ({@link FluidFlow.Model#LEGACY 1.8} vs {@link FluidFlow.Model#MODERN 26.1}). See {@link VelocityConfig#flowModel}. */
        public Builder flowModel(FluidFlow.Model v) { flowModel = v; return this; }
        /** Enable/disable the {@link FluidFlow.Model#MODERN} lava current (26 pushes in lava; Hypixel does not). See {@link VelocityConfig#flowLava}. */
        public Builder flowLava(boolean v) { flowLava = v; return this; }
        /** Select the ladder/climb model ({@link ClimbModel#LEGACY 1.8} vs {@link ClimbModel#MODERN 26.1}). See {@link VelocityConfig#climbModel}. */
        public Builder climbModel(ClimbModel v) { climbModel = v; return this; }
        /** Enable/disable the 26-only block velocity behaviors (sweet-berry/powder-snow stuck + bed bounce). See {@link VelocityConfig#modernBlockPhysics}. */
        public Builder modernBlockPhysics(boolean v) { modernBlockPhysics = v; return this; }

        // the attacker self-slowdown lives on AttackConfig.fullHitScale (an attack-time mutation), not here

        public VelocityConfig build() {
            return new VelocityConfig(seed, launchOffset,
                    clampX, clampY, clampZ, groundTicks, maxAirTicks, entityPush, fluidPhysics, climbPhysics, webPhysics, flowPush, flowModel, flowLava, climbModel, modernBlockPhysics);
        }
    }
}
