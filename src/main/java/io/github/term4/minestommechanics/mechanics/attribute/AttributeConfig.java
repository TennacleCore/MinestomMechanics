package io.github.term4.minestommechanics.mechanics.attribute;
import io.github.term4.minestommechanics.mechanics.attribute.source.Source;

import io.github.term4.minestommechanics.config.Config;
import io.github.term4.minestommechanics.config.FieldValue;
import io.github.term4.minestommechanics.mechanics.attribute.AttributeConfigResolver.AttributeContext;
import io.github.term4.minestommechanics.mechanics.attribute.defense.ArmorConfig;
import io.github.term4.minestommechanics.mechanics.attribute.defense.ProtectionConfig;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Immutable attribute-system config: the {@link Source} catalog, the {@code enabled} switch, and per-source <em>tunings</em>.
 * A {@link Config} resolvable per scope (player → instance → global). Version = which source variants you register
 * ({@code Strength.LEGACY} vs {@code MODERN}); per-scope {@link Builder#disable}/{@link Builder#scale}/{@link Builder#tune} without a new source. Use {@link #builder()}.
 */
public final class AttributeConfig extends Config<AttributeContext, AttributeConfig> {

    /** A per-source transform on its resolved modifiers (disable / scale / arbitrary), applied in the source's scope. */
    @FunctionalInterface
    public interface Tuning {
        List<Source.Mod> apply(AttributeContext ctx, List<Source.Mod> mods);
    }

    /** The identity tuning (no change) - returned for any source without an override. */
    public static final Tuning IDENTITY = (ctx, mods) -> mods;

    public final FieldValue<AttributeContext, Boolean> enabled;
    private final List<Source> sources;
    private final Map<Key, Tuning> tunings;

    /** The armor defense stage (formula + enabled); {@code null} = no armor reduction. Set by the preset (LEGACY/MODERN). */
    @Nullable public final ArmorConfig armor;

    /** The EPF/Protection defense stage (formula + enabled); {@code null} = no enchant protection. Set by the preset (LEGACY/MODERN). */
    @Nullable public final ProtectionConfig protection;

    /**
     * Whether legacy "attribute swapping" is permitted (a held-item PvP tech). {@code true}: held modifiers ride the
     * per-tick reconcile (vanilla {@code detectEquipmentUpdates} timing), so a hotbar swap lags a tick - the exploitable
     * window. {@code false} (default, patched): held attributes refresh immediately on the slot change, closing it (Paper's
     * {@code updateEquipmentOnPlayerActions}). Armor always rides the tick (post-settle, avoiding the use-item prediction race); 1.8 has none, so inert there.
     */
    @Nullable public final Boolean attributeSwapping;

    private AttributeConfig(Builder b) {
        super(b.subConfig);
        this.enabled = b.enabled;
        this.sources = b.sources != null ? List.copyOf(b.sources) : List.of();
        this.tunings = b.tunings != null ? Map.copyOf(b.tunings) : Map.of();
        this.armor = b.armor;
        this.protection = b.protection;
        this.attributeSwapping = b.attributeSwapping;
    }

    /** Whether attribute swapping is permitted (held attributes lag a tick); {@code false} (default) patches it. */
    public boolean attributeSwapping() { return attributeSwapping != null && attributeSwapping; }

    /** Sources to register at install. */
    public List<Source> sources() { return sources; }

    /** The per-source tuning for {@code key} (its modifier transform), or {@link #IDENTITY} when unset. */
    public Tuning tuningFor(Key key) { return tunings.getOrDefault(key, IDENTITY); }

    /** Merges this config over base: this wins per key; tunings union with this overriding. */
    public AttributeConfig fromBase(AttributeConfig base) {
        Map<Key, Tuning> merged = new HashMap<>(base.tunings);
        merged.putAll(tunings);
        return new Builder()
                .subConfig(subConfig != null ? subConfig : base.subConfig)
                .enabled(merge(enabled, base.enabled))
                .sources(!sources.isEmpty() ? sources : base.sources)
                .tunings(merged)
                .armor(armor != null ? (base.armor != null ? armor.fromBase(base.armor) : armor) : base.armor)
                .protection(protection != null ? (base.protection != null ? protection.fromBase(base.protection) : protection) : base.protection)
                .attributeSwapping(attributeSwapping != null ? attributeSwapping : base.attributeSwapping)
                .build();
    }

    public Builder toBuilder() { return new Builder(this); }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private Function<AttributeContext, AttributeConfig> subConfig;
        private FieldValue<AttributeContext, Boolean> enabled;
        private List<Source> sources;
        private Map<Key, Tuning> tunings;
        private ArmorConfig armor;
        private ProtectionConfig protection;
        private Boolean attributeSwapping;

        Builder() {}

        Builder(AttributeConfig c) {
            subConfig = c.subConfig;
            enabled = c.enabled;
            sources = c.sources.isEmpty() ? null : new ArrayList<>(c.sources);
            tunings = c.tunings.isEmpty() ? null : new HashMap<>(c.tunings);
            armor = c.armor;
            protection = c.protection;
            attributeSwapping = c.attributeSwapping;
        }

        public Builder subConfig(Function<AttributeContext, AttributeConfig> fn) { subConfig = fn; return this; }
        public Builder enabled(Boolean v) { enabled = FieldValue.constant(v); return this; }
        public Builder enabled(Function<AttributeContext, Boolean> fn) { enabled = FieldValue.of(fn); return this; }
        public Builder enabled(Boolean fallback, Function<AttributeContext, Boolean> fn) { enabled = FieldValue.ofWithFallback(fallback, fn); return this; }

        /** Adds sources to the catalog (copying first, so a shared list isn't mutated). */
        public Builder sources(Source... add) {
            List<Source> list = sources == null ? new ArrayList<>() : new ArrayList<>(sources);
            for (Source s : add) list.add(s);
            sources = list;
            return this;
        }

        /** Turns a source off in this scope (it contributes nothing). */
        public Builder disable(Key sourceKey) { return tune(sourceKey, (ctx, mods) -> List.of()); }

        /** Scales a source's modifier amounts by {@code factor} in this scope. */
        public Builder scale(Key sourceKey, double factor) {
            return tune(sourceKey, (ctx, mods) -> {
                List<Source.Mod> out = new ArrayList<>(mods.size());
                for (Source.Mod m : mods) out.add(new Source.Mod(m.attribute(), m.operation(), m.amount() * factor));
                return out;
            });
        }

        /** Replaces a source's modifier output in this scope with an arbitrary transform. */
        public Builder tune(Key sourceKey, Tuning tuning) {
            if (tunings == null) tunings = new HashMap<>();
            tunings.put(sourceKey, tuning);
            return this;
        }

        /** The armor defense stage (formula + enabled). */
        public Builder armor(ArmorConfig v) { armor = v; return this; }

        /** The EPF/Protection defense stage (formula + enabled). */
        public Builder protection(ProtectionConfig v) { protection = v; return this; }

        /** Whether to permit attribute swapping ({@code true} = the held-swap lag window stays open; {@code false}/unset = patched). See {@link AttributeConfig#attributeSwapping}. */
        public Builder attributeSwapping(Boolean v) { attributeSwapping = v; return this; }

        Builder enabled(FieldValue<AttributeContext, Boolean> v) { enabled = v; return this; }
        Builder sources(List<Source> v) { sources = v; return this; }
        Builder tunings(Map<Key, Tuning> v) { tunings = v; return this; }

        public AttributeConfig build() { return new AttributeConfig(this); }
    }
}
