package io.github.term4.minestommechanics.presets.hypixel;

import io.github.term4.minestommechanics.tracking.motion.ClimbModel;
import io.github.term4.minestommechanics.tracking.motion.FluidFlow;
import io.github.term4.minestommechanics.tracking.motion.VelocityConfig;
import io.github.term4.minestommechanics.tracking.motion.VelocityRule;

/** Hypixel movement: the custom simulated-arc velocity rule (set ONCE on a {@code MechanicsProfile.velocity(...)} scope). */
public final class Movement {

    private Movement() {}

    /**
     * The Hypixel velocity tracking method - the melee friction fold, the hurt broadcast, and projectile knockback all
     * read it (configs leave their {@code velocity} knob unset).
     */
    public static VelocityRule velocity() {
        return VelocityRule.simulated(arcConfig());
    }

    /**
     * Shared simulated-arc knobs. Hypixel's vertical KB does NOT apply vanilla's motY &lt; 0.005 near-zero clamp (the apex
     * "reseed"); with it on, the descending arc folds ~0.003 b/t too low (~11 wire-shorts by the third hit). clampY(0)
     * disables the apex reseed for this arc only; clampX/clampZ stay vanilla. entityPush(false) because Hypixel disables
     * player collision (no server-side push residual to fold). flowModel(MODERN.withLegacyWaterGravity()) because Hypixel
     * runs a modern server for the HORIZONTAL current (averaged + depth-scaled, not wall-zeroed - unlike 1.8's flat,
     * wall-zeroing {@code LEGACY}) but keeps the 1.8 VERTICAL water gravity (0.02 vs modern's 0.005): a victim hit in water
     * takes 0.39 vertical KB, not 0.3975. flowLava(false): Hypixel does NOT push in lava (water only), unlike vanilla 26.
     * climbModel(MODERN): folds ladder climb-up while ascending, and detects the full climbable tag - 1.8's LEGACY never
     * fires climb-up server-side (up == down == the slide value).
     */
    private static VelocityConfig arcConfig() {
        return VelocityConfig.builder()
                .clampY(0)
                .entityPush(false)
                .flowModel(FluidFlow.Model.MODERN.withLegacyWaterGravity())
                .flowLava(false)
                .climbModel(ClimbModel.MODERN)
                .build();
    }
}
