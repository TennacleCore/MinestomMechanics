package io.github.term4.minestommechanics.mechanics.consumable;

import io.github.term4.minestommechanics.Services;
import io.github.term4.minestommechanics.config.FieldValue;
import io.github.term4.minestommechanics.mechanics.consumable.ConsumableTypeConfig.ParticleVisibility;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.PlayerHand;
import net.minestom.server.item.ItemAnimation;
import net.minestom.server.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Resolves a {@link ConsumableConfig} + a {@link Consumable} type into plain values, against a {@link ConsumableContext}.
 * Mirrors {@code ProjectileConfigResolver}: the effective per-consumable config is layered (active config's per-type
 * override -&gt; its generic {@link ConsumableConfig#defaults()} -&gt; the type's {@code defaultConfig()} -&gt; hard
 * fallbacks), then each knob is resolved against the context.
 */
public final class ConsumableConfigResolver {

    private ConsumableConfigResolver() {}

    /**
     * The context the per-consumable {@code FieldValue}s resolve against, and that the {@link ConsumableBehavior} hooks
     * receive: the consuming {@code user}, the {@code item} being consumed and the {@code hand} holding it, the
     * {@link Consumable} definition, and {@link Services} (so a behavior can reach hunger / attributes / etc.).
     */
    public record ConsumableContext(Player user, ItemStack item, PlayerHand hand, Consumable consumable, @Nullable Services services) {

        /**
         * Effective per-consumable config, layered highest-first: {@code cfg}'s per-type override -&gt; its generic
         * {@link ConsumableConfig#defaults()} -&gt; the type's {@code defaultConfig()}, plus any {@code subConfig} overlay.
         */
        public ConsumableTypeConfig typeConfig(@Nullable ConsumableConfig cfg) {
            ConsumableTypeConfig tc = cfg != null ? cfg.typeConfig(consumable.key()) : null;
            ConsumableTypeConfig generic = cfg != null ? cfg.defaults() : null;
            ConsumableTypeConfig base = consumable.defaultConfig();
            if (generic != null) base = generic.fromBase(base);
            if (tc != null) base = tc.fromBase(base);
            if (base.subConfig != null) {
                ConsumableTypeConfig overlay = base.subConfig.apply(this);
                if (overlay != null) base = overlay.fromBase(base);
            }
            return base;
        }

        /**
         * The resolved {@link ParticleVisibility} for this consume (the {@code particles} knob off the scope's config),
         * default {@link ParticleVisibility#SHOWN}. The built-in effect behaviors read it to flag {@code addEffect}, so a
         * potion's particle behaviour is a config knob rather than behavior code. Resolve once per behavior (it re-reads the config).
         */
        public ParticleVisibility particles() {
            ConsumableSystem sys = services != null ? services.consumables() : null;
            ConsumableTypeConfig tc = typeConfig(sys != null ? sys.configFor(user) : null);
            ParticleVisibility pv = tc.particles != null ? tc.particles.resolve(this) : null;
            return pv != null ? pv : ParticleVisibility.SHOWN;
        }
    }

    /** Resolves the effective consumable values for {@code ctx} under {@code cfg}. */
    public static ResolvedConsumable resolve(@Nullable ConsumableConfig cfg, ConsumableContext ctx) {
        ConsumableTypeConfig tc = ctx.typeConfig(cfg);
        return new ResolvedConsumable(
                or(resolve(tc.enabled, ctx), Boolean.TRUE),
                or(resolve(tc.consumeTicks, ctx), Consumable.VANILLA_CONSUME_TICKS),
                or(resolve(tc.animation, ctx), ItemAnimation.EAT),
                or(resolve(tc.behavior, ctx), ConsumableBehavior.NONE));
    }

    private static <T> T or(@Nullable T v, T def) { return v != null ? v : def; }

    private static <T> @Nullable T resolve(@Nullable FieldValue<ConsumableContext, T> fv, ConsumableContext ctx) {
        return fv != null ? fv.resolve(ctx) : null;
    }

    /** Resolved per-consume values: whether it consumes, how long it takes, the animation, and the behavior. */
    public record ResolvedConsumable(boolean enabled, int consumeTicks, ItemAnimation animation, ConsumableBehavior behavior) {}
}
