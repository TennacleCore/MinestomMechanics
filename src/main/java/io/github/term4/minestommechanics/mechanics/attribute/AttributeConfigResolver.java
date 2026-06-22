package io.github.term4.minestommechanics.mechanics.attribute;
import io.github.term4.minestommechanics.mechanics.attribute.source.Source;

import io.github.term4.minestommechanics.Services;
import io.github.term4.minestommechanics.config.FieldValue;
import net.kyori.adventure.key.Key;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.item.ItemStack;
import net.minestom.server.potion.TimedPotion;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Resolves AttributeConfig with context into plain values, and is the query a consumer reads. Mirrors DamageConfigResolver. */
public final class AttributeConfigResolver {

    private AttributeConfigResolver() {}

    /**
     * Resolution + query context for one entity (and the in-context item) under the scope-resolved {@code config}. The
     * active sources are scanned lazily off {@link AttributeSystem}; each source's modifiers pass through the config's
     * per-source {@link AttributeConfig#tuningFor tuning} before they fold (disable / scale / custom).
     */
    public record AttributeContext(LivingEntity entity, @Nullable ItemStack item, AttributeConfig config, Services services,
                                   Map<FactKey<?>, Object> facts) {

        public static AttributeContext of(LivingEntity entity, @Nullable ItemStack item, AttributeConfig config, Services services) {
            return new AttributeContext(entity, item, config, services, Map.of());
        }

        /** The optional fact for {@code key} (e.g. {@code CombatFacts.TARGET}), or {@code null} if the calling domain didn't set it. */
        @SuppressWarnings("unchecked")
        public <T> @Nullable T fact(FactKey<T> key) { return (T) facts.get(key); }

        /** A copy of this context with {@code key -> value} added (a no-op if {@code value} is null) - the domain drops in its facts. */
        public <T> AttributeContext with(FactKey<T> key, @Nullable T value) {
            if (value == null) return this;
            Map<FactKey<?>, Object> next = new HashMap<>(facts);
            next.put(key, value);
            return new AttributeContext(entity, item, config, services, next);
        }

        private List<AttributeSystem.Active> active() {
            AttributeSystem sys = services != null ? services.attributes() : null;
            if (sys == null) return List.of();
            if (Boolean.FALSE.equals(resolve(config.enabled, this))) return List.of();
            return sys.activeSources(entity, item);
        }

        /** A source's modifiers after the scope's per-source tuning (disable -> empty, scale, etc.). */
        private List<Source.Mod> tuned(AttributeSystem.Active a) {
            return config.tuningFor(a.source().key()).apply(this, a.source().modifiers(a.level(), this));
        }

        /**
         * {@code base} folded with the active (tuned) modifiers for {@code attr} via the shared 3-operation math
         * (add -> multiply-base -> multiply-total). Works for vanilla (server-authored base) and custom attributes (base 0).
         */
        public double value(Attribute attr, double base) {
            double add = 0, multBase = 0, multTotal = 1;
            for (AttributeSystem.Active a : active()) {
                for (Source.Mod m : tuned(a)) {
                    if (!m.attribute().equals(attr)) continue;
                    switch (m.operation()) {
                        case ADD_VALUE -> add += m.amount();
                        case ADD_MULTIPLIED_BASE -> multBase += m.amount();
                        case ADD_MULTIPLIED_TOTAL -> multTotal *= (1 + m.amount());
                    }
                }
            }
            return (base + add) * (1 + multBase) * multTotal;
        }

        /** The active level (amplifier + 1) of the potion effect {@code effectKey} on this entity, or 0 if absent. */
        public int effectLevel(Key effectKey) {
            for (TimedPotion tp : entity.getActiveEffects()) {
                if (tp.potion().effect().key().equals(effectKey)) return tp.potion().amplifier() + 1;
            }
            return 0;
        }
    }

    public static ResolvedAttributeConfig resolve(AttributeConfig config, AttributeContext ctx) {
        AttributeConfig cfg = config;
        if (cfg.subConfig != null) {
            AttributeConfig sub = cfg.subConfig.apply(ctx);
            if (sub != null) cfg = sub.fromBase(cfg);
        }
        return new ResolvedAttributeConfig(resolve(cfg.enabled, ctx));
    }

    private static <T> @Nullable T resolve(@Nullable FieldValue<AttributeContext, T> fv, AttributeContext ctx) {
        return fv != null ? fv.resolve(ctx) : null;
    }

    /** Resolved config with plain values (nullable when unset). */
    public record ResolvedAttributeConfig(@Nullable Boolean enabled) {}
}
