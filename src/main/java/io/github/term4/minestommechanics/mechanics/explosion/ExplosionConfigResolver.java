package io.github.term4.minestommechanics.mechanics.explosion;

import io.github.term4.minestommechanics.Services;
import io.github.term4.minestommechanics.config.FieldValue;
import net.minestom.server.coordinate.Point;
import net.minestom.server.entity.Entity;
import net.minestom.server.instance.Instance;
import org.jetbrains.annotations.Nullable;

/** Resolves {@link ExplosionConfig} with context into plain values. Mirrors KnockbackConfigResolver. */
public final class ExplosionConfigResolver {

    private ExplosionConfigResolver() {}

    /** Resolution context for one explosion (not per-victim: power/exposure falloff are computed by the system per entity). */
    public record ExplosionContext(Instance instance, Point center, @Nullable Entity source, Services services) {
        public static ExplosionContext of(Instance instance, Point center, @Nullable Entity source, Services services) {
            return new ExplosionContext(instance, center, source, services);
        }
    }

    /** Plain values coalesced to the modern-vanilla baseline; the 1.8 deltas (damageConstant 8.0, floored) come from the config. */
    public static ResolvedExplosionConfig resolve(@Nullable ExplosionConfig config, ExplosionContext ctx) {
        ExplosionConfig cfg = config;
        if (cfg != null && cfg.subConfig != null) {
            ExplosionConfig sub = cfg.subConfig.apply(ctx);
            if (sub != null) cfg = sub.fromBase(cfg);
        }
        return new ResolvedExplosionConfig(
                cfg != null ? resolve(cfg.power, ctx) : null,
                or(cfg != null ? resolve(cfg.damageConstant, ctx) : null, 7.0),
                Boolean.TRUE.equals(cfg != null ? resolve(cfg.floorDamage, ctx) : null),
                or(cfg != null ? resolve(cfg.knockbackMultiplier, ctx) : null, 1.0),
                !Boolean.FALSE.equals(cfg != null ? resolve(cfg.exposure, ctx) : null),
                Boolean.TRUE.equals(cfg != null ? resolve(cfg.fire, ctx) : null),
                Boolean.TRUE.equals(cfg != null ? resolve(cfg.affectsSource, ctx) : null));
    }

    private static <T> @Nullable T resolve(@Nullable FieldValue<ExplosionContext, T> fv, ExplosionContext ctx) {
        return fv != null ? fv.resolve(ctx) : null;
    }

    private static double or(@Nullable Double v, double fallback) {
        return v != null ? v : fallback;
    }

    /** Resolved explosion knobs. {@code power} is {@code null} when neither the call nor the config set one (the system defaults it). */
    public record ResolvedExplosionConfig(
            @Nullable Double power,
            double damageConstant,
            boolean floorDamage,
            double knockbackMultiplier,
            boolean exposure,
            boolean fire,
            boolean affectsSource
    ) {}
}
