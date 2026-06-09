package io.github.term4.minestommechanics.tracking;

import net.minestom.server.collision.Aerodynamics;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;

/**
 * Vanilla 1.8 movement constants the velocity arc reconstructs from - the canonical home for the numbers
 * {@link VelocityRule} and {@link MotionTracker} both read. The three air-resistance terms ({@link #gravity},
 * {@link #horizontalAirResistance}, {@link #verticalAirResistance}) mirror Minestom's {@link Aerodynamics} and are
 * sourced live from an entity via {@link #fromEntity}; the rest are 1.8 constants Minestom does not model. Build
 * overrides with {@link #builder()} / {@link #toBuilder()}.
 *
 * @param gravity                 downward acceleration per tick (vanilla {@code 0.08}).
 * @param horizontalAirResistance airborne horizontal drag multiplier (vanilla {@code 0.91}).
 * @param verticalAirResistance   vertical drag multiplier (vanilla {@code 0.98}).
 * @param slipperiness            default block slipperiness (vanilla {@code 0.6}); folds into {@link #groundFriction()}.
 * @param jumpVelocity            launch vertical seed (vanilla {@code bF()}/{@code bE()} = {@code 0.42}).
 * @param sprintImpulseX          sprint-jump boost on the {@code -sin(yaw)} (x) axis (vanilla {@code 0.2}).
 * @param sprintImpulseZ          sprint-jump boost on the {@code cos(yaw)} (z) axis (vanilla {@code 0.2}).
 * @param clampX                  near-zero clamp for {@code motX} (vanilla {@code 0.005}); below this is zeroed each tick.
 * @param clampY                  near-zero clamp for {@code motY} (vanilla {@code 0.005}); also drives the arc apex reseed.
 * @param clampZ                  near-zero clamp for {@code motZ} (vanilla {@code 0.005}).
 */
public record Physics(
        double gravity,
        double horizontalAirResistance,
        double verticalAirResistance,
        double slipperiness,
        double jumpVelocity,
        double sprintImpulseX,
        double sprintImpulseZ,
        double clampX,
        double clampY,
        double clampZ
) {

    /** Terminal vertical velocity (b/t): the fixed point of {@code motY -> (motY - gravity) * verticalAirResistance} ({@code -3.92} vanilla). */
    public double terminalVy() {
        return -gravity * verticalAirResistance / (1.0 - verticalAirResistance);
    }

    /** On-ground horizontal friction per tick = {@link #slipperiness} x {@link #horizontalAirResistance} ({@code ~0.546} vanilla). */
    public double groundFriction() {
        return slipperiness * horizontalAirResistance;
    }

    /**
     * Sprint-jump horizontal impulse for a facing yaw (b/t) - the bare {@code bF()} boost
     * ({@code motX -= sin(yaw) * sprintImpulseX}, {@code motZ += cos(yaw) * sprintImpulseZ}). Double-precision
     * sin/cos, so it carries ~1e-5 vs vanilla's float {@code MathHelper} table (below knockback quantization).
     */
    public Vec sprintJumpImpulse(double yaw) {
        double r = Math.toRadians(yaw);
        return new Vec(-Math.sin(r) * sprintImpulseX, 0, Math.cos(r) * sprintImpulseZ);
    }

    /** The three air-resistance terms as Minestom's {@link Aerodynamics}, for {@link net.minestom.server.collision.PhysicsUtils#updateVelocity}. */
    public Aerodynamics toAerodynamics() {
        return new Aerodynamics(gravity, horizontalAirResistance, verticalAirResistance);
    }

    /** The canonical vanilla 1.8 constants. */
    public static Physics vanilla() {
        return new Physics(0.08, 0.91, 0.98, 0.6, 0.42, 0.2, 0.2, 0.005, 0.005, 0.005);
    }

    /** {@link #vanilla()} with the three air-resistance terms taken from {@code aero}. */
    public static Physics fromAerodynamics(Aerodynamics aero) {
        return builder()
                .gravity(aero.gravity())
                .airResistance(aero.horizontalAirResistance(), aero.verticalAirResistance())
                .build();
    }

    /** {@link #fromAerodynamics} sourced from the entity's current {@link Entity#getAerodynamics()}. */
    public static Physics fromEntity(Entity entity) {
        return fromAerodynamics(entity.getAerodynamics());
    }

    public Builder toBuilder() { return new Builder(this); }
    public static Builder builder() { return new Builder(); }

    /** Builder over the vanilla defaults; set only the constants you want to change. */
    public static final class Builder {
        private double gravity = 0.08;
        private double horizontalAirResistance = 0.91;
        private double verticalAirResistance = 0.98;
        private double slipperiness = 0.6;
        private double jumpVelocity = 0.42;
        private double sprintImpulseX = 0.2;
        private double sprintImpulseZ = 0.2;
        private double clampX = 0.005;
        private double clampY = 0.005;
        private double clampZ = 0.005;

        Builder() {}

        Builder(Physics p) {
            gravity = p.gravity;
            horizontalAirResistance = p.horizontalAirResistance;
            verticalAirResistance = p.verticalAirResistance;
            slipperiness = p.slipperiness;
            jumpVelocity = p.jumpVelocity;
            sprintImpulseX = p.sprintImpulseX;
            sprintImpulseZ = p.sprintImpulseZ;
            clampX = p.clampX;
            clampY = p.clampY;
            clampZ = p.clampZ;
        }

        public Builder gravity(double v) { gravity = v; return this; }
        public Builder horizontalAirResistance(double v) { horizontalAirResistance = v; return this; }
        public Builder verticalAirResistance(double v) { verticalAirResistance = v; return this; }
        public Builder airResistance(double horizontal, double vertical) { horizontalAirResistance = horizontal; verticalAirResistance = vertical; return this; }
        public Builder slipperiness(double v) { slipperiness = v; return this; }
        public Builder jumpVelocity(double v) { jumpVelocity = v; return this; }
        public Builder sprintImpulse(double both) { sprintImpulseX = both; sprintImpulseZ = both; return this; }
        public Builder sprintImpulse(double x, double z) { sprintImpulseX = x; sprintImpulseZ = z; return this; }
        public Builder clamp(double all) { clampX = all; clampY = all; clampZ = all; return this; }
        public Builder clamp(double x, double y, double z) { clampX = x; clampY = y; clampZ = z; return this; }
        public Builder clampX(double v) { clampX = v; return this; }
        public Builder clampY(double v) { clampY = v; return this; }
        public Builder clampZ(double v) { clampZ = v; return this; }

        public Physics build() {
            return new Physics(gravity, horizontalAirResistance, verticalAirResistance, slipperiness, jumpVelocity,
                    sprintImpulseX, sprintImpulseZ, clampX, clampY, clampZ);
        }
    }
}
