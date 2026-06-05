package io.github.term4.minestommechanics.tracking;

import net.minestom.server.coordinate.Vec;

/**
 * Strategy for estimating an entity's velocity (blocks/tick) used by the knockback friction term.
 * Mirrors the rule pattern of {@code AttackEvent.CriticalRule} / {@code DamageEvent.OverdamageRule}:
 * a single-method interface with a {@link #DEFAULT} and static factories for the built-in strategies.
 *
 * <p>{@link #estimate(VelocityContext)} receives a rich {@link VelocityContext}, so a rule supplied
 * from outside the library composes the same public primitives the built-ins use - an open rule, not
 * a fixed menu. Wire one in via {@code KnockbackConfig.velocityMethod(...)} (or the per-axis
 * {@code velocityMode*} setters).
 *
 * <p>Built-ins:
 * <ul>
 *   <li>{@link #delta()} - trust the client's reported motion (raw position-delta blocks/tick).</li>
 *   <li>{@link #vanilla()} - 1:1 vanilla reconstruction: the gravity-predicted vertical (iterated with
 *       vanilla's near-zero apex reseed) plus the decayed sprint-jump horizontal impulse.</li>
 *   <li>{@link #vanillaClosed()} - {@link #vanilla()} with the closed-form vertical arc (skips the apex
 *       reseed, ~0.0015 b/t high in {@code kb.y} on a descending hit); a cheaper approximation.</li>
 *   <li>{@link #hypixel()} - the closed-form gravity arc one tick further along (offset {@code -1}), which
 *       is what lines up 1:1 with Hypixel's whole-tick velocity sheet.</li>
 * </ul>
 */
@FunctionalInterface
public interface VelocityRule {

    /** Estimated velocity (blocks/tick) for the knockback friction term. */
    Vec estimate(VelocityContext ctx);

    /** Rule used when a config does not specify one. */
    VelocityRule DEFAULT = vanilla();

    /** Position delta (trusts the client's reported motion, includes knockback). */
    static VelocityRule delta() { return VelocityContext::positionDelta; }

    /**
     * 1:1 vanilla reconstruction. The vertical is the launched-aware gravity arc on whole air ticks: vanilla
     * advances the victim's server-side {@code motY} once per tick, so a hit folds exactly {@code vy(t)} for an
     * integer air-tick {@code t}. The horizontal is the player's own sprint-jump impulse, bled by air friction
     * (x{@code 0.91}/tick, as vanilla's {@code g(0,0)} does).
     */
    static VelocityRule vanilla() {
        return clampNearZero(gravityRule(VelocityContext.VANILLA_LAUNCH_OFFSET, true), VelocityContext.NEAR_ZERO_CLAMP);
    }

    /**
     * {@link #vanilla()} with the closed-form arc ({@code v0*0.98^t - 3.92*(1-0.98^t)}) instead of the
     * clamp-iterated one. It skips vanilla's apex reseed, so it runs ~0.003 b/t shallow per descending tick
     * (air-tick 7: {@code -0.15234} vs vanilla's {@code -0.15523}). Same {@code -2} offset; a cheaper
     * closed-form approximation.
     */
    static VelocityRule vanillaClosed() {
        return clampNearZero(gravityRule(VelocityContext.VANILLA_LAUNCH_OFFSET, false), VelocityContext.NEAR_ZERO_CLAMP);
    }

    /**
     * Hypixel's velocity tracking: the closed-form gravity arc one tick further along than vanilla (offset
     * {@code -1} vs {@code -2}). The closed form - not vanilla's clamp-iterated arc with its apex reseed - is
     * what matches Hypixel's whole-tick velocity sheet 1:1 on the vertical.
     */
    static VelocityRule hypixel() {
        return clampNearZero(gravityRule(-1, false), VelocityContext.NEAR_ZERO_CLAMP);
    }

    /**
     * Wraps a rule so each component of its estimate is zeroed when {@code |component| < threshold}, mirroring
     * vanilla {@code m()}'s near-zero velocity clamp ({@code motX/motY/motZ < 0.005 -> 0}). Configurable per
     * rule by design: the gravity-arc rules ({@link #vanilla()}/{@link #hypixel()}) use the vanilla
     * {@link VelocityContext#NEAR_ZERO_CLAMP} (0.005), while a {@link #delta()} rule (unclamped by default) can
     * be wrapped with its own threshold, e.g. {@code clampNearZero(delta(), 0.0001)}. {@code threshold <= 0}
     * returns the rule unchanged.
     */
    static VelocityRule clampNearZero(VelocityRule rule, double threshold) {
        if (threshold <= 0) return rule;
        return ctx -> {
            Vec v = rule.estimate(ctx);
            return new Vec(
                    Math.abs(v.x()) < threshold ? 0.0 : v.x(),
                    Math.abs(v.y()) < threshold ? 0.0 : v.y(),
                    Math.abs(v.z()) < threshold ? 0.0 : v.z());
        };
    }

    /**
     * Builds a gravity-arc rule. {@code launchOffset} is the per-rule whole-tick anchor for the launched arc
     * (vanilla {@code -2}, Hypixel {@code -1}). {@code clampArc} picks the vertical arc: {@code true} iterates
     * with the near-zero apex reseed ({@link #gravityVy}, vanilla-faithful), {@code false} uses the closed form
     * ({@link #gravityVyClosed}).
     */
    private static VelocityRule gravityRule(int launchOffset, boolean clampArc) {
        return ctx -> gravityArc(ctx, launchOffset, clampArc);
    }

    /**
     * The shared "air-tick gravity tracked value": the sprint-jump horizontal impulse (when launched and
     * sprinting), decayed by air friction ({@code 0.91} per air tick, mirroring vanilla's {@code g(0,0)}
     * {@code motX *= 0.91}), plus the gravity-predicted vertical for the current air-time.
     */
    private static Vec gravityArc(VelocityContext ctx, int launchOffset, boolean clampArc) {
        VelocityContext.JumpInfo j = ctx.recentJump();
        double hx = 0, hz = 0;
        if (j != null) {
            // seedH is the player's actual this.motX/motZ at takeoff that the tracker maintained exactly like
            // vanilla: each bF() folds a 0.2 boost onto the surviving residual (prior takeoff bled by air
            // friction in flight and ground friction while grounded). So a jump from rest seeds ~0.2 and a
            // continuous bunny-hop seeds ~0.248 - no fixed magic number. We then bleed it by motX *= 0.91 over
            // the same air-tick count the vertical arc uses (launchOffset aligns our clock to vanilla's).
            Vec h = j.seedH();
            double decayTicks = Math.max(0.0, ctx.ticksInAir() + launchOffset);
            double decay = Math.pow(VelocityContext.AIR_FRICTION_H, decayTicks);
            hx = h.x() * decay;
            hz = h.z() * decay;
        }
        double vy = clampArc ? gravityVy(ctx, launchOffset) : gravityVyClosed(ctx, launchOffset);
        return new Vec(hx, vy, hz);
    }

    /**
     * Gravity-predicted vertical (b/t), iterated EXACTLY as vanilla 1.8 evolves {@code motY}: each tick
     * {@code if (|motY| < 0.005) motY = 0} (the {@code m()} near-zero clamp), then {@code motY = (motY-0.08)*0.98}.
     * Seeded with {@code v0 = 0.42} when launched, else a walk-off from rest.
     *
     * <p>The clamp is load-bearing: at the apex the carried {@code motY} drops below {@code 0.005} and is
     * zeroed, so the descent reseeds from 0 (the closed form smooths through and runs ~0.003 b/t shallow per
     * descending tick). {@code launchOffset} aligns our clock to the folded air-tick ({@code -2} vanilla,
     * {@code -1} Hypixel; see {@link VelocityContext#VANILLA_LAUNCH_OFFSET}); walk-off uses {@code ticksInAir + 1}.
     */
    private static double gravityVy(VelocityContext ctx, int launchOffset) {
        boolean launched = ctx.launched();
        int ticks = launched ? ctx.ticksInAir() + launchOffset : ctx.ticksInAir() + 1;
        return arcVy(ticks, launched);
    }

    /**
     * The vanilla 1.8 vertical arc value (b/t) after {@code ticks} whole gravity ticks from the launch
     * ({@code v0 = 0.42}) or walk-off (rest) seed, iterated with the {@code m()} near-zero clamp so the apex
     * reseed is exact (see {@link #gravityVy}). {@code ticks <= 0} returns the seed.
     */
    private static double arcVy(int ticks, boolean launched) {
        if (ticks <= 0) return launched ? VelocityContext.JUMP_Y : 0.0;
        double g = VelocityContext.GRAVITY_PER_TICK;
        double s = VelocityContext.GRAVITY_SCALE;
        double clamp = VelocityContext.NEAR_ZERO_CLAMP;
        double motY = launched ? VelocityContext.JUMP_Y : 0.0;
        for (int i = 0; i < ticks; i++) {
            if (Math.abs(motY) < clamp) motY = 0.0; // vanilla m() near-zero clamp: zeroes the apex -> reseeds the fall
            motY = (motY - g) * s;
            if (motY < VelocityContext.TERMINAL_VY) motY = VelocityContext.TERMINAL_VY;
        }
        return motY;
    }

    /**
     * Closed-form vanilla arc {@code v0*0.98^t - 3.92*(1 - 0.98^t)} used by {@link #vanillaClosed()} and
     * {@link #hypixel()}. No apex reseed (it smooths straight through {@code motY = 0}), so it runs ~0.003 b/t
     * shallow per descending tick versus {@link #gravityVy}.
     */
    private static double gravityVyClosed(VelocityContext ctx, int launchOffset) {
        double g = VelocityContext.GRAVITY_PER_TICK;
        double s = VelocityContext.GRAVITY_SCALE;
        boolean launched = ctx.launched();
        int ticks = launched ? ctx.ticksInAir() + launchOffset : ctx.ticksInAir() + 1;
        if (ticks <= 0) return -g * s; // matches the old closed-form rule's launch-tick value (Hypixel 1:1)
        double scalePow = Math.pow(s, ticks);
        double v0 = launched ? VelocityContext.JUMP_Y : 0.0;
        double vy = v0 * scalePow - g * s * (1 - scalePow) / (1 - s);
        return Math.max(VelocityContext.TERMINAL_VY, Math.min(VelocityContext.JUMP_Y, vy));
    }
}
