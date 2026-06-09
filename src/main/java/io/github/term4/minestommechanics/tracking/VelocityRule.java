package io.github.term4.minestommechanics.tracking;

import net.minestom.server.collision.PhysicsUtils;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;

/**
 * Strategy for estimating an entity's velocity (b/t) for the knockback friction term. A single-method interface
 * with a {@link #DEFAULT} and static factories, mirroring {@code AttackEvent.CriticalRule}.
 *
 * <p>Two base rules, mixable per axis with {@link #split}:
 * <ul>
 *   <li>{@link #simulated(ArcSpec)} - reconstruct the launch arc: seed the jump ({@link Physics#jumpVelocity()})
 *       and the sprint-jump impulse (folded into {@code seedH} by {@link MotionTracker}), then advance per tick
 *       with Minestom's {@link PhysicsUtils#updateVelocity}. Per-axis {@link ArcStyle}.</li>
 *   <li>{@link #delta()} - trust the client's reported motion (position delta, which includes knockback).</li>
 * </ul>
 */
@FunctionalInterface
public interface VelocityRule {

    /** How the {@link #simulated(ArcSpec) simulated} arc evaluates an axis. */
    enum ArcStyle {
        /**
         * Step per tick via {@link PhysicsUtils#updateVelocity}, zeroing {@code motY} below {@link Physics#clampY()}
         * before each step so the apex reseeds from 0 - vanilla-faithful 1.8.
         */
        PER_TICK,
        /** Analytic {@code v0*s^t - g*s*(1 - s^t)/(1 - s)} (no per-tick loop); smooths through the apex - cheaper. */
        CLOSED
    }

    /** Estimated velocity (b/t) for the knockback friction term. */
    Vec estimate(VelocityContext ctx);

    /**
     * Air-tick correction: vanilla's server-side {@code this.motY} arc lags the victim's true (client) air-time by
     * ~2 ticks, so a hit clocked at {@code ticksInAir = T} folds what vanilla folds at {@code T - 2}. Calibrated 1:1
     * against a local vanilla 1.8 server; also the basis the launched horizontal residual decays on.
     */
    int VANILLA_LAUNCH_OFFSET = -2;

    /**
     * Hypixel's offset, one tick further along than vanilla. Their vertical KB is a pure air-tick gravity prediction
     * (fixed {@code jumpVelocity} seed, never re-seeded by the hit just dealt), whose whole-tick velocity sheet lines
     * up with the victim's true air-time at {@code -1} - calibrated 1:1 against the sheet: a chained hit at air-tick
     * 19 reads the sheet's tick-19 value ({@code -0.020750}), not a re-seeded pop. (The {@code -2} that briefly looked
     * right was the old per-hit motY re-seed inflating the late-combo fold; with the re-seed gone, {@code -1} matches
     * the sheet at both the early {@code t:9} and the late {@code t:19} anchors.)
     */
    int HYPIXEL_LAUNCH_OFFSET = -1;

    /** Rule used when a config does not specify one (the default {@link #simulated()} arc). */
    VelocityRule DEFAULT = simulated();

    /** Position delta - trusts the client's reported motion. Unclamped. */
    static VelocityRule delta() { return VelocityContext::positionDelta; }

    /**
     * Position delta averaged over {@code sampleRate} ticks. TODO: scaffolded - honouring {@code sampleRate > 1}
     * needs an N-tick position history in {@link MotionTracker}; until then this is the 1-tick {@link #delta()}.
     */
    static VelocityRule delta(int sampleRate) { return VelocityContext::positionDelta; }

    /** Reconstructed launch arc with vanilla-default knobs. */
    static VelocityRule simulated() { return simulated(ArcSpec.defaults()); }

    /** Reconstructed launch arc with the given {@link ArcSpec}. */
    static VelocityRule simulated(ArcSpec spec) { return ctx -> arc(ctx, spec); }

    /** Mixes two rules per axis: horizontal (x/z) from {@code horizontal}, vertical (y) from {@code vertical}. */
    static VelocityRule split(VelocityRule horizontal, VelocityRule vertical) {
        return ctx -> {
            Vec h = horizontal.estimate(ctx);
            return new Vec(h.x(), vertical.estimate(ctx).y(), h.z());
        };
    }

    /** Wraps a rule, zeroing each component below its threshold (vanilla {@code m()} near-zero clamp). */
    static VelocityRule clampNearZero(VelocityRule rule, double x, double y, double z) {
        return ctx -> clamp(rule.estimate(ctx), x, y, z);
    }

    /** Fluent per-axis composition: pick a {@link #simulated()}/{@link #delta()} source per axis + a unified output clamp. */
    static Builder builder() { return new Builder(); }

    /**
     * Reconstructs the launch arc: seeds the jump + sprint-jump (from {@code seedH}), advances by
     * {@code ticksInAir + launchOffset} ticks (a walk-off seeds from rest at {@code ticksInAir + 1}), evaluates each
     * axis by its {@link ArcStyle}, and applies the per-component clamp. The air clock free-runs through a combo (the
     * vertical seed is the fixed jump velocity planted at launch, never re-seeded from the knockback just dealt), so a
     * juggled victim's fold decays down the gravity curve and the combo ends naturally. Constants come from the spec's
     * {@link ArcSpec#physics()} override, else the entity's own {@link VelocityContext#physics()}.
     *
     * <p>The air clock is gated on the spec's {@link ArcSpec#groundRule()} (a per-entity override still wins): a
     * grounded victim resets to {@code ticksInAir = 0}, unlaunched, so it folds the resting {@code -g*s} step rather
     * than a stale descent. This mirrors vanilla, which clocks its {@code this.motY} arc off the {@code move()}
     * collision result, not the client {@code onGround} flag - so a laggy victim that has truly landed takes ground
     * knockback, not air knockback, exactly as vanilla.
     */
    private static Vec arc(VelocityContext ctx, ArcSpec spec) {
        Physics ph = spec.physics() != null ? spec.physics() : ctx.physics();
        boolean grounded = ctx.grounded(spec.groundRule());
        boolean launched = !grounded && ctx.launched();
        int air = grounded ? 0 : ctx.ticksInAir();
        int ticks = launched ? air + spec.launchOffset() : air + 1;
        MotionTracker.JumpInfo j = ctx.recentJump();
        Vec seedH = j != null ? j.seedH() : Vec.ZERO;
        double seedY = spec.seed() != null ? spec.seed() : ph.jumpVelocity();
        // TODO (jumpBoost scaffold): when spec.jumpBoost(), add (JUMP amplifier + 1) * 0.1 to seedY.
        Vec seed = launched ? new Vec(seedH.x(), seedY, seedH.z()) : Vec.ZERO;

        boolean usePerTick = spec.horizontalStyle() == ArcStyle.PER_TICK || spec.verticalStyle() == ArcStyle.PER_TICK;
        boolean useClosed = spec.horizontalStyle() == ArcStyle.CLOSED || spec.verticalStyle() == ArcStyle.CLOSED;
        Vec stepped = usePerTick ? simulate(ctx.entity(), ph, seed, ticks) : Vec.ZERO;
        Vec analytic = useClosed ? closedArc(ph, seed, ticks, launched) : Vec.ZERO;

        Vec h = spec.horizontalStyle() == ArcStyle.CLOSED ? analytic : stepped;
        Vec v = spec.verticalStyle() == ArcStyle.CLOSED ? analytic : stepped;
        return clamp(new Vec(h.x(), v.y(), h.z()), ph.clampX(), ph.clampY(), ph.clampZ());
    }

    /**
     * Advances {@code seed} by {@code ticks} airborne ticks via {@link PhysicsUtils#updateVelocity} (gravity + air
     * resistance, {@code onGround=false}), zeroing {@code motY} below {@link Physics#clampY()} before each step. The
     * block getter is unused while airborne, so a null instance is harmless.
     */
    private static Vec simulate(Entity entity, Physics ph, Vec seed, int ticks) {
        if (ticks <= 0) return seed;
        Vec vel = seed;
        var aero = ph.toAerodynamics();
        var pos = entity.getPosition();
        var blocks = entity.getInstance();
        double clampY = ph.clampY();
        for (int t = 0; t < ticks; t++) {
            if (Math.abs(vel.y()) < clampY) vel = vel.withY(0);
            vel = PhysicsUtils.updateVelocity(pos, vel, blocks, aero, true, false, false, false);
        }
        return vel;
    }

    /**
     * Analytic arc: horizontal {@code seedH * hAir^ticks}, vertical {@code v0*s^t - g*s*(1 - s^t)/(1 - s)}. No apex
     * reseed (~0.003 b/t shallow per descending tick vs {@link #simulate}). {@code ticks <= 0} returns the
     * launch-tick {@code -g*s}.
     */
    private static Vec closedArc(Physics ph, Vec seed, int ticks, boolean launched) {
        double g = ph.gravity();
        double s = ph.verticalAirResistance();
        double hpow = Math.pow(ph.horizontalAirResistance(), Math.max(0, ticks));
        double hx = seed.x() * hpow;
        double hz = seed.z() * hpow;
        double vy;
        if (ticks <= 0) {
            vy = -g * s;
        } else {
            double scalePow = Math.pow(s, ticks);
            double v0 = launched ? seed.y() : 0.0;
            vy = v0 * scalePow - g * s * (1 - scalePow) / (1 - s);
            vy = Math.max(ph.terminalVy(), Math.min(ph.jumpVelocity(), vy));
        }
        return new Vec(hx, vy, hz);
    }

    private static Vec clamp(Vec v, double cx, double cy, double cz) {
        return new Vec(
                Math.abs(v.x()) < cx ? 0.0 : v.x(),
                Math.abs(v.y()) < cy ? 0.0 : v.y(),
                Math.abs(v.z()) < cz ? 0.0 : v.z());
    }

    /**
     * Per-axis composition of two source rules plus one unified output clamp. Both axes default to
     * {@link #simulated()}; override either via {@link #horizontal}/{@link #vertical}, then optionally {@link #clamp}
     * the final vector. {@link #build()} folds to {@link #split} (wrapped in {@link #clampNearZero} when a clamp is set).
     */
    final class Builder {
        private VelocityRule horizontal = DEFAULT;
        private VelocityRule vertical = DEFAULT;
        private double clampX, clampY, clampZ;
        private boolean clamp;

        Builder() {}

        /** Source for the x/z axes (default {@link #simulated()}). */
        public Builder horizontal(VelocityRule rule) { this.horizontal = rule; return this; }

        /** Source for the y axis (default {@link #simulated()}). */
        public Builder vertical(VelocityRule rule) { this.vertical = rule; return this; }

        /** Uniform near-zero output clamp on every axis. */
        public Builder clamp(double all) { return clamp(all, all, all); }

        /** Per-axis near-zero output clamp on the composed vector. */
        public Builder clamp(double x, double y, double z) {
            this.clampX = x;
            this.clampY = y;
            this.clampZ = z;
            this.clamp = true;
            return this;
        }

        public VelocityRule build() {
            VelocityRule rule = split(horizontal, vertical);
            return clamp ? clampNearZero(rule, clampX, clampY, clampZ) : rule;
        }
    }
}
