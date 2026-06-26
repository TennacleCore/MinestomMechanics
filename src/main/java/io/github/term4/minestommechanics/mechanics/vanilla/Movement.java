package io.github.term4.minestommechanics.mechanics.vanilla;

import io.github.term4.minestommechanics.tracking.motion.ClimbModel;
import io.github.term4.minestommechanics.tracking.motion.FluidFlow;
import io.github.term4.minestommechanics.tracking.motion.VelocityConfig;
import io.github.term4.minestommechanics.tracking.motion.VelocityRule;

/** Modern (26.1) movement: the velocity tracking method. (Modern player config is still TODO.) */
public final class Movement {

    private Movement() {}

    /**
     * Modern (26.1) velocity tracking method: server-arc reconstruction with the pure-modern fluid current
     * ({@link FluidFlow.Model#MODERN} - averaged + depth-scaled water and lava, {@link VelocityConfig#flowLava} on). Set
     * on a {@code MechanicsProfile.velocity(...)} scope. ({@code vanilla18.Movement.velocity()} is the flat 1.8 {@code LEGACY}.)
     */
    public static VelocityRule velocity() {
        return VelocityRule.simulated(VelocityConfig.builder()
                .flowModel(FluidFlow.Model.MODERN)
                .climbModel(ClimbModel.MODERN)
                .modernBlockPhysics(true)
                .build());
    }
}
