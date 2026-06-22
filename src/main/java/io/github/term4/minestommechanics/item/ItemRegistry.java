package io.github.term4.minestommechanics.item;

import net.minestom.server.entity.LivingEntity;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.OptionalDouble;

/**
 * Registry of per-item gameplay stats (weapons / tools), version-aware. One lookup, {@link #value}, resolves: the
 * registered value for the active {@link ItemDef.Version}, else the {@link ItemStat}'s Minestom-derived default, else the
 * caller's fallback. The active version is set per preset; {@link ItemDef}s carry both versions for clarity/override.
 * Armor is NOT here - its aggregate rides Minestom's {@code ARMOR} attribute (version-identical points). Holds only
 * what diverges from Minestom (e.g. 1.8 weapon damage).
 */
public final class ItemRegistry {

    private final ItemDef.Version version;
    private final Map<Material, ItemDef> defs;

    public ItemRegistry(ItemDef.Version version, ItemDef... defs) {
        this.version = version;
        Map<Material, ItemDef> map = new HashMap<>();
        for (ItemDef d : defs) map.put(d.material(), d);
        this.defs = Map.copyOf(map);
    }

    public ItemDef.Version version() { return version; }

    /** Register or override an item def; returns a new registry (immutable). */
    public ItemRegistry register(ItemDef def) {
        Map<Material, ItemDef> map = new HashMap<>(defs);
        map.put(def.material(), def);
        return new ItemRegistry(version, map.values().toArray(ItemDef[]::new));
    }

    /**
     * The {@code stat} for the held {@code item} (and optional {@code holder}, used by some Minestom-derived defaults):
     * the registered value for the active version, else the stat's Minestom default, else {@code fallback}.
     */
    public double value(@Nullable ItemStack item, @Nullable LivingEntity holder, ItemStat stat, double fallback) {
        if (item != null && !item.isAir()) {
            ItemDef def = defs.get(item.material());
            if (def != null) {
                OptionalDouble stored = def.stored(version, stat);
                if (stored.isPresent()) return stored.getAsDouble();
            }
            double derived = stat.minestomDefault(item, holder);
            if (!Double.isNaN(derived)) return derived;
        }
        return fallback;
    }
}
