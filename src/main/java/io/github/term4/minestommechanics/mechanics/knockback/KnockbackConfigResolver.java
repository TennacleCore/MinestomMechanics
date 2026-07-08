package io.github.term4.minestommechanics.mechanics.knockback;

import io.github.term4.minestommechanics.MechanicsKeys;
import io.github.term4.minestommechanics.Services;
import io.github.term4.minestommechanics.config.FieldValue;
import io.github.term4.minestommechanics.tracking.motion.LegacyVelocity;
import io.github.term4.minestommechanics.tracking.motion.MotionTracker;
import io.github.term4.minestommechanics.tracking.SprintTracker;
import io.github.term4.minestommechanics.tracking.motion.VelocityContext;
import io.github.term4.minestommechanics.tracking.motion.VelocityRule;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Resolves KnockbackConfig with context into plain values. */
public final class KnockbackConfigResolver {

    private KnockbackConfigResolver() {}

    public record KnockbackContext(KnockbackSnapshot snap, Services services,
                                   @Nullable VelocityRule resolvedVelocity) {
        public static KnockbackContext of(KnockbackSnapshot snap, Services services) {
            return new KnockbackContext(snap, services, null);
        }
        /**
         * Derives a context carrying the velocity rule the calculator resolved for this hit, so a
         * {@link KnockbackComponent} reads the same velocity the friction fold used via {@link #victimVelocity()}.
         */
        public KnockbackContext withVelocity(@Nullable VelocityRule rule) {
            return new KnockbackContext(snap, services, rule);
        }
        public boolean victimOnGround() {
            return MotionTracker.onGround(snap.target());
        }
        /** Whether the attacker sprinted within the config's {@code sprintBuffer} window - the same definition the pipeline's extra-level gate uses. */
        public boolean sprint() {
            var a = snap.source();
            if (!(a instanceof Player p) || services.sprintTracker() == null) return false;
            KnockbackConfig cfg = snap.config();
            Integer buf = cfg != null && cfg.sprintBuffer != null ? cfg.sprintBuffer.resolve(this) : null;
            return SprintTracker.wasRecentlySprinting(services.sprintTracker(), p, buf != null ? buf : 0);
        }
        /**
         * The effective victim-velocity rule: the rule {@link #withVelocity threaded} by the calculator when present,
         * else resolved standalone (config velocity -&gt; victim scope -&gt; {@link VelocityRule#DEFAULT}).
         */
        public VelocityRule velocityRule() {
            if (resolvedVelocity != null) return resolvedVelocity;
            KnockbackConfig cfg = snap.config();
            VelocityRule rule = cfg != null && cfg.velocity != null ? cfg.velocity.resolve(this) : null;
            if (rule == null && services != null) rule = services.profiles().resolve(snap.target(), MechanicsKeys.VELOCITY);
            return rule != null ? rule : VelocityRule.DEFAULT;
        }
        /**
         * The resolved victim velocity (b/t) - the same value the friction fold uses - by estimating {@link #velocityRule()}
         * for the target ({@link Vec#ZERO} when none). Lets a custom {@link KnockbackComponent} read it without pinning a rule onto the config.
         */
        public Vec victimVelocity() {
            Entity t = snap.target();
            if (t == null) return Vec.ZERO;
            return velocityRule().estimate(VelocityContext.of(t, services != null ? services.sprintTracker() : null));
        }
    }

    public static ResolvedKnockbackConfig resolve(KnockbackConfig config, KnockbackContext ctx) {
        KnockbackConfig cfg = config;
        if (cfg.subConfig != null) {
            KnockbackConfig sub = cfg.subConfig.apply(ctx);
            if (sub != null) cfg = sub.fromBase(cfg);
        }
        // no knockback-level invul window (gating is the attack processor's job)
        return new ResolvedKnockbackConfig(
                or(resolve(cfg.sprintBuffer, ctx), 0),
                or(resolve(cfg.horizontal, ctx), 0.0),
                or(resolve(cfg.vertical, ctx), 0.0),
                or(resolve(cfg.extraHorizontal, ctx), 0.0),
                or(resolve(cfg.extraVertical, ctx), 0.0),
                resolve(cfg.horizontalBounds, ctx),
                resolve(cfg.verticalBounds, ctx),
                resolve(cfg.extraHorizontalBounds, ctx),
                resolve(cfg.extraVerticalBounds, ctx),
                or(resolve(cfg.yawWeight, ctx), 0.0),
                or(resolve(cfg.extraYawWeight, ctx), 0.0),
                or(resolve(cfg.pitchWeight, ctx), 0.0),
                or(resolve(cfg.extraPitchWeight, ctx), 0.0),
                or(resolve(cfg.heightDelta, ctx), 0.0),
                or(resolve(cfg.extraHeightDelta, ctx), 0.0),
                or(resolve(cfg.horizontalCombine, ctx), KnockbackConfig.DirectionMode.SCALAR),
                or(resolve(cfg.verticalCombine, ctx), KnockbackConfig.DirectionMode.SCALAR),
                or(resolve(cfg.frictionH, ctx), 0.0), // 0 = no fold of the victim's velocity
                or(resolve(cfg.frictionV, ctx), 0.0),
                or(resolve(cfg.frictionModeH, ctx), KnockbackConfig.FrictionMode.DIVISOR),
                or(resolve(cfg.frictionModeV, ctx), KnockbackConfig.FrictionMode.DIVISOR),
                resolve(cfg.velocity, ctx),
                or(resolve(cfg.quantizeVelocity, ctx), Boolean.TRUE),
                or(resolve(cfg.velocityCap, ctx), LegacyVelocity.DEFAULT_CAP), // vanilla 1.8 wire ±3.9
                or(resolve(cfg.airborneVertical, ctx), Boolean.TRUE), // 1.8 always lifts
                cfg.customComponents,
                cfg.frictionRule,
                cfg.combineRule,
                cfg.boundsRule,
                cfg.stages
        );
    }

    private static <T> T resolve(@Nullable FieldValue<KnockbackContext, T> fv, KnockbackContext ctx) {
        return fv != null ? fv.resolve(ctx) : null;
    }

    private static <T> T or(@Nullable T v, T def) {
        return v != null ? v : def;
    }

    /** Resolved plain values, defaults coalesced here; {@code null} only where unset is semantic (bounds = no clamp, velocity = the victim's scope chain). */
    public record ResolvedKnockbackConfig(
            int sprintBuffer,
            double horizontal,
            double vertical,
            double extraHorizontal,
            double extraVertical,
            @Nullable KnockbackConfig.Bounds horizontalBounds,
            @Nullable KnockbackConfig.Bounds verticalBounds,
            @Nullable KnockbackConfig.Bounds extraHorizontalBounds,
            @Nullable KnockbackConfig.Bounds extraVerticalBounds,
            double yawWeight,
            double extraYawWeight,
            double pitchWeight,
            double extraPitchWeight,
            double heightDelta,
            double extraHeightDelta,
            KnockbackConfig.DirectionMode horizontalCombine,
            KnockbackConfig.DirectionMode verticalCombine,
            double frictionH,
            double frictionV,
            KnockbackConfig.FrictionMode frictionModeH,
            KnockbackConfig.FrictionMode frictionModeV,
            @Nullable VelocityRule velocity,
            boolean quantizeVelocity,
            double velocityCap,
            boolean airborneVertical,
            @Nullable List<KnockbackComponent> customComponents,
            @Nullable KnockbackConfig.FrictionRule frictionRule,
            @Nullable KnockbackConfig.CombineRule combineRule,
            @Nullable KnockbackConfig.BoundsRule boundsRule,
            @Nullable List<KnockbackPipeline.Stage> stages
    ) {}
}
