package io.github.term4.minestommechanics.mechanics.damage;

import io.github.term4.minestommechanics.api.event.DamageEvent;
import io.github.term4.minestommechanics.config.Config;
import io.github.term4.minestommechanics.config.FieldValue;
import io.github.term4.minestommechanics.mechanics.damage.DamageConfigResolver.DamageContext;
import io.github.term4.minestommechanics.mechanics.damage.types.DamageTypeConfig;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/** Immutable damage config. Use {@link #builder()}, {@link #toBuilder()}. Mirrors KnockbackConfig. */
public final class DamageConfig extends Config<DamageContext, DamageConfig> {

    public final FieldValue<DamageContext, Integer> invulTicks;
    public final FieldValue<DamageContext, Boolean> enableOverdamage;
    public final FieldValue<DamageContext, DamageEvent.OverdamageRule> overdamageRule;
    public final FieldValue<DamageContext, Boolean> silent;
    public final FieldValue<DamageContext, Boolean> overdamageSilent;

    /** Per-type config overrides, keyed by {@link DamageTypeConfig#key()}. Unset knobs fall back to this config. */
    public final Map<Key, DamageTypeConfig> typeConfigs;

    private DamageConfig(Builder b) {
        super(b.subConfig);
        invulTicks = b.invulTicks;
        enableOverdamage = b.enableOverdamage;
        overdamageRule = b.overdamageRule;
        silent = b.silent;
        overdamageSilent = b.overdamageSilent;
        typeConfigs = Map.copyOf(b.typeConfigs);
    }

    /** Per-type config for {@code key}, or {@code null} if none was registered. */
    public @Nullable DamageTypeConfig typeConfig(Key key) {
        return typeConfigs.get(key);
    }

    /** Merges this config over base. */
    public DamageConfig fromBase(DamageConfig base) {
        Map<Key, DamageTypeConfig> mergedTypes = new LinkedHashMap<>(base.typeConfigs);
        mergedTypes.putAll(typeConfigs);
        return new Builder()
                .subConfig(subConfig != null ? subConfig : base.subConfig)
                .invulTicks(merge(invulTicks, base.invulTicks))
                .enableOverdamage(merge(enableOverdamage, base.enableOverdamage))
                .overdamageRule(merge(overdamageRule, base.overdamageRule))
                .silent(merge(silent, base.silent))
                .overdamageSilent(merge(overdamageSilent, base.overdamageSilent))
                .typeConfigs(mergedTypes)
                .build();
    }

    public Builder toBuilder() {
        return new Builder(this);
    }

    public static Builder builder() {
        return builder(null);
    }

    public static Builder builder(@Nullable DamageConfig base) {
        return base != null ? new Builder(base) : new Builder();
    }

    public static final class Builder {
        private Function<DamageContext, DamageConfig> subConfig;
        private FieldValue<DamageContext, Integer> invulTicks;
        private FieldValue<DamageContext, Boolean> enableOverdamage;
        private FieldValue<DamageContext, DamageEvent.OverdamageRule> overdamageRule;
        private FieldValue<DamageContext, Boolean> silent;
        private FieldValue<DamageContext, Boolean> overdamageSilent;
        private final Map<Key, DamageTypeConfig> typeConfigs = new LinkedHashMap<>();

        Builder() {}

        Builder(DamageConfig c) {
            subConfig = c.subConfig;
            invulTicks = c.invulTicks;
            enableOverdamage = c.enableOverdamage;
            overdamageRule = c.overdamageRule;
            silent = c.silent;
            overdamageSilent = c.overdamageSilent;
            typeConfigs.putAll(c.typeConfigs);
        }

        public Builder subConfig(Function<DamageContext, DamageConfig> fn) { subConfig = fn; return this; }
        public Builder invulTicks(Integer v) { invulTicks = FieldValue.constant(v); return this; }
        public Builder invulTicks(Function<DamageContext, Integer> fn) { invulTicks = FieldValue.of(fn); return this; }
        public Builder invulTicks(Integer fallback, Function<DamageContext, Integer> fn) { invulTicks = FieldValue.ofWithFallback(fallback, fn); return this; }
        public Builder enableOverdamage(Boolean v) { enableOverdamage = FieldValue.constant(v); return this; }
        public Builder enableOverdamage(Function<DamageContext, Boolean> fn) { enableOverdamage = FieldValue.of(fn); return this; }
        public Builder enableOverdamage(Boolean fallback, Function<DamageContext, Boolean> fn) { enableOverdamage = FieldValue.ofWithFallback(fallback, fn); return this; }

        public Builder overdamageRule(DamageEvent.OverdamageRule v) { overdamageRule = FieldValue.constant(v); return this; }
        public Builder overdamageRule(Function<DamageContext, DamageEvent.OverdamageRule> fn) { overdamageRule = FieldValue.of(fn); return this; }
        public Builder overdamageRule(DamageEvent.OverdamageRule fallback, Function<DamageContext, DamageEvent.OverdamageRule> fn) { overdamageRule = FieldValue.ofWithFallback(fallback, fn); return this; }

        public Builder silent(Boolean v) { silent = FieldValue.constant(v); return this; }
        public Builder silent(Function<DamageContext, Boolean> fn) { silent = FieldValue.of(fn); return this; }
        public Builder silent(Boolean fallback, Function<DamageContext, Boolean> fn) { silent = FieldValue.ofWithFallback(fallback, fn); return this; }

        public Builder overdamageSilent(Boolean v) { overdamageSilent = FieldValue.constant(v); return this; }
        public Builder overdamageSilent(Function<DamageContext, Boolean> fn) { overdamageSilent = FieldValue.of(fn); return this; }
        public Builder overdamageSilent(Boolean fallback, Function<DamageContext, Boolean> fn) { overdamageSilent = FieldValue.ofWithFallback(fallback, fn); return this; }

        /** Adds a single per-type config override, keyed by its {@link DamageTypeConfig#key()}. */
        public Builder typeConfig(DamageTypeConfig cfg) { typeConfigs.put(cfg.key(), cfg); return this; }

        /** Adds per-type config overrides, each keyed by its {@link DamageTypeConfig#key()}. */
        public Builder typeConfigs(DamageTypeConfig... cfgs) {
            for (DamageTypeConfig cfg : cfgs) typeConfigs.put(cfg.key(), cfg);
            return this;
        }

        Builder invulTicks(FieldValue<DamageContext, Integer> v) { invulTicks = v; return this; }
        Builder enableOverdamage(FieldValue<DamageContext, Boolean> v) { enableOverdamage = v; return this; }
        Builder overdamageRule(FieldValue<DamageContext, DamageEvent.OverdamageRule> v) { overdamageRule = v; return this; }
        Builder silent(FieldValue<DamageContext, Boolean> v) { silent = v; return this; }
        Builder overdamageSilent(FieldValue<DamageContext, Boolean> v) { overdamageSilent = v; return this; }
        Builder typeConfigs(Map<Key, DamageTypeConfig> cfgs) { typeConfigs.putAll(cfgs); return this; }

        public DamageConfig build() {
            return new DamageConfig(this);
        }
    }
}
