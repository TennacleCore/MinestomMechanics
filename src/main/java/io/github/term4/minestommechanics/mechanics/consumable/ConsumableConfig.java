package io.github.term4.minestommechanics.mechanics.consumable;

import io.github.term4.minestommechanics.config.Config;
import io.github.term4.minestommechanics.mechanics.consumable.ConsumableConfigResolver.ConsumableContext;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Immutable consumable config: a generic {@link #defaults} base plus per-consumable {@link ConsumableTypeConfig}
 * overrides keyed by type key. Mirrors {@code ProjectileConfig}; assigned per scope via the
 * {@link io.github.term4.minestommechanics.MechanicsProfile} {@code consumables} member. Resolution layers per-type
 * override -&gt; {@link #defaults} -&gt; the registered type's {@code defaultConfig()} -&gt; hard fallbacks.
 *
 * <p>The consumable <em>definitions</em> (the {@link Consumable} types + their materials) live in the
 * {@link ConsumableRegistry}; this config only tunes them. A scope disables all consumables by setting
 * {@code defaults(ConsumableTypeConfig.builder().enabled(false).build())}.
 */
public final class ConsumableConfig extends Config<ConsumableContext, ConsumableConfig> {

    /** Generic base applied to every consumable unless its own entry overrides a knob ({@code null} = none). */
    public final @Nullable ConsumableTypeConfig defaults;
    /** Per-consumable config overrides, keyed by {@link ConsumableTypeConfig#key()}. */
    public final Map<Key, ConsumableTypeConfig> typeConfigs;
    /** Consumable type identities (key + material) this config registers. Read once at install, from the global profile. */
    public final List<Consumable> types;

    private ConsumableConfig(Builder b) {
        super(b.subConfig);
        this.defaults = b.defaults;
        this.typeConfigs = Map.copyOf(b.typeConfigs);
        this.types = List.copyOf(b.types);
    }

    /** The generic base config every consumable inherits, or {@code null} if none set. */
    public @Nullable ConsumableTypeConfig defaults() { return defaults; }

    /** Per-consumable config for {@code key}, or {@code null} if none registered. */
    public @Nullable ConsumableTypeConfig typeConfig(Key key) { return typeConfigs.get(key); }

    /** The consumable type identities this config registers. */
    public List<Consumable> types() { return types; }

    /** Merges this config over base (this config's generic defaults + per-type entries + types layered over base's). */
    public ConsumableConfig fromBase(ConsumableConfig base) {
        Map<Key, ConsumableTypeConfig> merged = new LinkedHashMap<>(base.typeConfigs);
        merged.putAll(typeConfigs);
        List<Consumable> mergedTypes = new ArrayList<>(base.types);
        mergedTypes.addAll(types);
        ConsumableTypeConfig mergedDefaults = defaults == null ? base.defaults
                : base.defaults == null ? defaults : defaults.fromBase(base.defaults);
        return new Builder()
                .subConfig(subConfig != null ? subConfig : base.subConfig)
                .defaults(mergedDefaults)
                .typeConfigs(merged)
                .types(mergedTypes)
                .build();
    }

    public Builder toBuilder() { return new Builder(this); }
    public static Builder builder() { return new Builder(); }
    public static Builder builder(@Nullable ConsumableConfig base) { return base != null ? new Builder(base) : new Builder(); }

    public static final class Builder {
        private Function<ConsumableContext, ConsumableConfig> subConfig;
        private @Nullable ConsumableTypeConfig defaults;
        private final Map<Key, ConsumableTypeConfig> typeConfigs = new LinkedHashMap<>();
        private final List<Consumable> types = new ArrayList<>();

        Builder() {}
        Builder(ConsumableConfig c) { subConfig = c.subConfig; defaults = c.defaults; typeConfigs.putAll(c.typeConfigs); types.addAll(c.types); }

        public Builder subConfig(Function<ConsumableContext, ConsumableConfig> fn) { subConfig = fn; return this; }
        /** Sets the generic base config every consumable inherits (its knobs apply unless a per-type entry overrides them). */
        public Builder defaults(@Nullable ConsumableTypeConfig generic) { this.defaults = generic; return this; }

        /** Adds per-consumable config overrides, each keyed by its {@link ConsumableTypeConfig#key()}. */
        public Builder typeConfigs(ConsumableTypeConfig... cfgs) {
            for (ConsumableTypeConfig c : cfgs) typeConfigs.put(c.key(), c);
            return this;
        }

        Builder typeConfigs(Map<Key, ConsumableTypeConfig> cfgs) { typeConfigs.putAll(cfgs); return this; }

        /** Adds consumable type identities (key + material) this config registers. */
        public Builder types(Consumable... t) { for (Consumable c : t) types.add(c); return this; }

        Builder types(List<Consumable> t) { types.addAll(t); return this; }

        public ConsumableConfig build() { return new ConsumableConfig(this); }
    }
}
