package io.github.term4.minestommechanics.tracking.motion;

import net.minestom.server.collision.Aerodynamics;
import net.minestom.server.collision.PhysicsUtils;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

/**
 * Strategy for estimating an entity's velocity (b/t) for the knockback friction term. The base rule is
 * {@link #simulated(VelocityConfig) simulated}: the reconstructed server-tracked velocity (vanilla
 * {@code motX/motY/motZ}) - vertical from {@link MotionTracker}'s motY sim, horizontal from its impulse residual +
 * entity push + fluid flow. Configured per preset via {@link VelocityConfig}; {@link #split} mixes two rules per axis.
 * Functional, so a from-scratch lambda works.
 */
@FunctionalInterface
public interface VelocityRule {

    /** Estimated velocity (b/t) for the knockback friction term. */
    Vec estimate(VelocityContext ctx);

    /**
     * The reconstruction toggles {@link MotionTracker} reads off this rule (fluid/climb/web/flow/model). {@code null} =
     * gate defaults (media handling on, flow off). The seam that makes a custom rule first-class; arc knobs live in {@link #arc}.
     */
    default @Nullable VelocityConfig reconstructionConfig() { return null; }

    /** Rule used when a config does not specify one (the default {@link #simulated()} arc). */
    VelocityRule DEFAULT = simulated();

    /** Server-tracked velocity with vanilla-default knobs. */
    static VelocityRule simulated() { return simulated(VelocityConfig.defaults()); }

    /** Server-tracked velocity with the given {@link VelocityConfig}. */
    static VelocityRule simulated(VelocityConfig cfg) { return new Simulated(cfg); }

    /** A {@link #simulated(VelocityConfig)} rule that exposes its {@link VelocityConfig} so {@link MotionTracker} reads its toggles. */
    record Simulated(VelocityConfig config) implements VelocityRule {
        @Override public Vec estimate(VelocityContext ctx) { return arc(ctx, config); }
        @Override public VelocityConfig reconstructionConfig() { return config; }
    }

    /** The reconstruction config for {@code rule} ({@code null} when it carries none - the default gate behaviour applies). */
    private static @Nullable VelocityConfig configOf(@Nullable VelocityRule rule) {
        return rule == null ? null : rule.reconstructionConfig();
    }

    /** Whether built-in fluid (water/lava) handling is on for {@code rule} - on by default. See {@link VelocityConfig#fluidPhysics}. */
    static boolean fluidPhysicsEnabled(@Nullable VelocityRule rule) {
        VelocityConfig c = configOf(rule);
        return c == null || c.fluidPhysics();
    }

    /** Whether the tracker's built-in <b>climb</b> (ladder/vine/climbable) handling is on for {@code rule} - on by default. See {@link VelocityConfig#climbPhysics}. */
    static boolean climbPhysicsEnabled(@Nullable VelocityRule rule) {
        VelocityConfig c = configOf(rule);
        return c == null || c.climbPhysics();
    }

    /** Whether the tracker's built-in <b>cobweb</b> handling is on for {@code rule} - on by default. See {@link VelocityConfig#webPhysics}. */
    static boolean webPhysicsEnabled(@Nullable VelocityRule rule) {
        VelocityConfig c = configOf(rule);
        return c == null || c.webPhysics();
    }

    /** Whether the fluid-flow residual is maintained/folded for {@code rule} - only when its config has it on (a from-scratch rule never inherits it). */
    static boolean flowPushEnabled(@Nullable VelocityRule rule) {
        VelocityConfig c = configOf(rule);
        return c != null && c.flowPush();
    }

    /** The {@link FluidFlow.Model} for {@code rule} ({@code LEGACY} when it carries no config). */
    static FluidFlow.Model flowModel(@Nullable VelocityRule rule) {
        VelocityConfig c = configOf(rule);
        return c != null ? c.flowModel() : FluidFlow.Model.LEGACY;
    }

    /** The ladder/climb model for {@code rule} ({@link ClimbModel#LEGACY} when the rule carries no config). Read once per tick. See {@link VelocityConfig#climbModel}. */
    static ClimbModel climbModel(@Nullable VelocityRule rule) {
        VelocityConfig c = configOf(rule);
        return c != null ? c.climbModel() : ClimbModel.LEGACY;
    }

    /** Whether the 26-only block velocity behaviors (sweet-berry/powder-snow stuck + bed bounce) are on for {@code rule} - {@code false} (1.8) when the rule carries no config. See {@link VelocityConfig#modernBlockPhysics}. */
    static boolean modernBlockPhysicsEnabled(@Nullable VelocityRule rule) {
        VelocityConfig c = configOf(rule);
        return c != null && c.modernBlockPhysics();
    }

    /** Whether the {@link FluidFlow.Model#MODERN} lava current is folded for {@code rule} (26 yes, Hypixel no); {@code false} when the rule carries no config. */
    static boolean flowLavaEnabled(@Nullable VelocityRule rule) {
        VelocityConfig c = configOf(rule);
        return c != null && c.flowLava();
    }

    /** Whether the motY sim advances only on client move packets (MineMen) vs every tick; {@code false} when the rule carries no config. See {@link VelocityConfig#motYOnMovePacket}. */
    static boolean motYOnMovePacketEnabled(@Nullable VelocityRule rule) {
        VelocityConfig c = configOf(rule);
        return c != null && c.motYOnMovePacket();
    }

    /** Reconstructed arc with per-context knobs (e.g. a ping-scaled {@code groundTicks}); use over a config lambda when only arc knobs vary. */
    static VelocityRule simulated(Function<VelocityContext, VelocityConfig> cfg) {
        return ctx -> arc(ctx, cfg.apply(ctx));
    }

    /**
     * Mixes two rules per axis: x/z from {@code horizontal}, y from {@code vertical}. A record (not a lambda) so the
     * reconstruction gates read their toggles off the {@code vertical} component (the one driving the motY sim).
     */
    record Split(VelocityRule horizontal, VelocityRule vertical) implements VelocityRule {
        @Override public Vec estimate(VelocityContext ctx) {
            Vec h = horizontal.estimate(ctx);
            return new Vec(h.x(), vertical.estimate(ctx).y(), h.z());
        }
        @Override public @Nullable VelocityConfig reconstructionConfig() { return vertical.reconstructionConfig(); }
    }

    /** Mixes two rules per axis (see {@link Split}). */
    static VelocityRule split(VelocityRule horizontal, VelocityRule vertical) {
        return new Split(horizontal, vertical);
    }

    /**
     * The server-tracked velocity fold (vanilla {@code motX/motY/motZ}): vertical from {@link #verticalMot}, horizontal
     * from {@link MotionTracker#horizontalMot} (the friction-bled sprint-jump residual, not the knockback), plus the
     * entity-push and flow residuals when enabled. Per-component clamps apply last. Non-players use their server velocity directly.
     */
    private static Vec arc(VelocityContext ctx, VelocityConfig cfg) {
        // non-players are server-simulated already; only players need the reconstruction below
        if (!(ctx.entity() instanceof Player)) {
            return clamp(ctx.positionDelta(), cfg.clampX(), cfg.clampY(), cfg.clampZ());
        }
        Vec hMot = MotionTracker.horizontalMot(ctx.entity(), cfg.launchOffset());
        Vec out = new Vec(hMot.x(), verticalMot(ctx, cfg), hMot.z());
        if (cfg.entityPush()) {
            Vec push = MotionTracker.entityPush(ctx.entity());
            out = out.add(push.x(), 0, push.z());
        }
        if (cfg.flowPush()) {
            // flow residual is 3D (x/z current + the Y down-term)
            Vec flow = MotionTracker.flowPush(ctx.entity());
            out = out.add(flow.x(), flow.y(), flow.z());
        }
        // wall-pinned mot reads 0 on the blocked axis (vanilla move() zeroing, measured)
        out = MotionTracker.zeroBlockedAxes(ctx.entity(), out);
        return clamp(out, cfg.clampX(), cfg.clampY(), cfg.clampZ());
    }

    /** Vertical mot: the live ticked sim ({@link MotionTracker#serverMotY}), or the air-clock {@link #reconstructedVy} for non-players / before the sim has ticked. */
    private static double verticalMot(VelocityContext ctx, VelocityConfig cfg) {
        Double simY = MotionTracker.serverMotY(ctx.entity(),
                VelocityConfig.DEFAULT_LAUNCH_OFFSET - cfg.launchOffset(), cfg.clampY() > 0);
        return simY != null ? simY : reconstructedVy(ctx, cfg);
    }

    /** Fallback vertical reconstruction from the air clock (non-players / pre-sim): seed at launch, step the air ticks, gated on ground state; {@code maxAirTicks} caps the clock. */
    private static double reconstructedVy(VelocityContext ctx, VelocityConfig cfg) {
        boolean grounded = ctx.onGround(cfg.groundTicks());
        boolean launched = !grounded && ctx.launched();
        int air = grounded ? 0 : ctx.ticksInAir();
        if (cfg.maxAirTicks() != null) air = Math.min(air, cfg.maxAirTicks());
        int ticks = launched ? air + cfg.launchOffset() : air + 1;
        double seedY = launched ? cfg.seed() : 0;
        return steppedVy(ctx.entity(), ctx.entity().getAerodynamics(), cfg.clampY(), seedY, ticks);
    }

    /** Advances {@code seedY} by {@code ticks} airborne ticks ({@link PhysicsUtils#updateVelocity}), apex-reseeding below {@code clampY} each step. */
    private static double steppedVy(Entity entity, Aerodynamics aero, double clampY, double seedY, int ticks) {
        if (ticks <= 0) return seedY;
        Vec vel = new Vec(0, seedY, 0);
        var pos = entity.getPosition();
        var blocks = entity.getInstance();
        for (int t = 0; t < ticks; t++) {
            if (Math.abs(vel.y()) < clampY) vel = vel.withY(0);
            vel = PhysicsUtils.updateVelocity(pos, vel, blocks, aero, true, false, false, false);
        }
        return vel.y();
    }

    private static Vec clamp(Vec v, double cx, double cy, double cz) {
        return new Vec(
                Math.abs(v.x()) < cx ? 0.0 : v.x(),
                Math.abs(v.y()) < cy ? 0.0 : v.y(),
                Math.abs(v.z()) < cz ? 0.0 : v.z());
    }
}
