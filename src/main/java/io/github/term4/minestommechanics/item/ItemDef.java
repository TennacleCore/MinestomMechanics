package io.github.term4.minestommechanics.item;

import net.minestom.server.item.Material;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalDouble;

/**
 * The gameplay stats for one item, with legacy and modern values side by side. A stat unset for a version falls back to
 * the {@link ItemStat}'s Minestom-derived default (see {@link ItemRegistry}), so you only store what diverges from
 * vanilla/Minestom. Build with {@link #of(Material)}.
 */
public final class ItemDef {

    /** Which value set a stat belongs to (1.8 vs 26); the active one is chosen by the {@link ItemRegistry}. */
    public enum Version { LEGACY, MODERN }

    private final Material material;
    private final Map<Version, Map<ItemStat, Double>> values;

    private ItemDef(Material material, Map<Version, Map<ItemStat, Double>> values) {
        this.material = material;
        this.values = values;
    }

    public Material material() { return material; }

    /** The stored value for {@code version}/{@code stat}, or empty to defer to the Minestom default. */
    public OptionalDouble stored(Version version, ItemStat stat) {
        Double v = values.getOrDefault(version, Map.of()).get(stat);
        return v != null ? OptionalDouble.of(v) : OptionalDouble.empty();
    }

    public static Builder of(Material material) { return new Builder(material); }

    public static final class Builder {
        private final Material material;
        private final Map<Version, Map<ItemStat, Double>> values = new EnumMap<>(Version.class);

        private Builder(Material material) { this.material = material; }

        /** Set both versions to the same value (use when 1.8 and 26 agree). */
        public Builder both(ItemStat stat, double value) { return legacy(stat, value).modern(stat, value); }
        public Builder legacy(ItemStat stat, double value) { return set(Version.LEGACY, stat, value); }
        public Builder modern(ItemStat stat, double value) { return set(Version.MODERN, stat, value); }

        private Builder set(Version version, ItemStat stat, double value) {
            values.computeIfAbsent(version, v -> new HashMap<>()).put(stat, value);
            return this;
        }

        public ItemDef build() { return new ItemDef(material, Map.copyOf(values)); }
    }
}
