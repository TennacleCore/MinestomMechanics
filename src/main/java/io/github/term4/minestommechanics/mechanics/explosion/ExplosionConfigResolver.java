package io.github.term4.minestommechanics.mechanics.explosion;

import io.github.term4.minestommechanics.Services;
import io.github.term4.minestommechanics.config.FieldValue;
import io.github.term4.minestommechanics.mechanics.attribute.defense.Bypass;
import io.github.term4.minestommechanics.mechanics.knockback.KnockbackConfig;
import net.minestom.server.coordinate.Point;
import net.minestom.server.entity.Entity;
import net.minestom.server.instance.Instance;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;
import java.util.function.Predicate;

/** Resolves {@link ExplosionConfig} with context into plain values. */
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
        ExplosionConfig cfg = config != null ? config.withOverlay(ctx) : null;
        return new ResolvedExplosionConfig(
                FieldValue.resolve(cfg != null ? cfg.power : null, ctx),
                FieldValue.resolve(cfg != null ? cfg.damageConstant : null, ctx, 7.0),
                FieldValue.resolve(cfg != null ? cfg.floorDamage : null, ctx, false),
                FieldValue.resolve(cfg != null ? cfg.flatDamage : null, ctx),
                FieldValue.resolve(cfg != null ? cfg.damageScale : null, ctx, 1.0),
                cfg != null ? cfg.damageBypass : null,
                FieldValue.resolve(cfg != null ? cfg.knockbackMultiplier : null, ctx, 1.0),
                cfg != null ? cfg.damageKnockback : null,
                FieldValue.resolve(cfg != null ? cfg.packetPush : null, ctx, true),
                FieldValue.resolve(cfg != null ? cfg.baseKnockback : null, ctx, 0.0),
                FieldValue.resolve(cfg != null ? cfg.baseHeight : null, ctx, 1.0),
                FieldValue.resolve(cfg != null ? cfg.baseHorizontalScale : null, ctx, 1.0),
                FieldValue.resolve(cfg != null ? cfg.baseDownwardScale : null, ctx, 1.0),
                FieldValue.resolve(cfg != null ? cfg.exposure : null, ctx, ExplosionExposure.Rays.MODERN),
                FieldValue.resolve(cfg != null ? cfg.knockbackImpactFloor : null, ctx),
                FieldValue.resolve(cfg != null ? cfg.fire : null, ctx, false),
                FieldValue.resolve(cfg != null ? cfg.affectsSource : null, ctx, false),
                cfg != null ? cfg.knockbackTargets : null,
                cfg != null ? cfg.pushEye : null);
    }

    /** Resolved explosion knobs. {@code power} is {@code null} when neither the call nor the config set one (the system defaults it). */
    public record ResolvedExplosionConfig(
            @Nullable Double power,
            double damageConstant,
            boolean floorDamage,
            @Nullable Double flatDamage,
            double damageScale,
            @Nullable Bypass damageBypass,
            double knockbackMultiplier,
            @Nullable KnockbackConfig damageKnockback,
            boolean packetPush,
            double baseKnockback,
            double baseHeight,
            double baseHorizontalScale,
            double baseDownwardScale,
            ExplosionExposure.Rays exposure,
            @Nullable Double knockbackImpactFloor,
            boolean fire,
            boolean affectsSource,
            @Nullable Predicate<Entity> knockbackTargets,
            @Nullable Function<Entity, Double> pushEye
    ) {}
}
