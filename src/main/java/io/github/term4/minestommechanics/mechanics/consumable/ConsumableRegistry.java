package io.github.term4.minestommechanics.mechanics.consumable;

import net.minestom.server.item.Material;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** {@link Material} -&gt; {@link Consumable} lookup for the {@link ConsumableSystem}; a consumable indexes under each of its materials. */
public final class ConsumableRegistry {

    private final Map<Material, Consumable> byMaterial = new ConcurrentHashMap<>();

    /** Registers {@code consumable} under each of its materials (last registration wins per material). */
    public void register(Consumable consumable) {
        for (Material m : consumable.materials()) byMaterial.put(m, consumable);
    }

    /** The consumable triggered by {@code material}, or {@code null}. */
    public @Nullable Consumable forMaterial(@Nullable Material material) {
        return material == null ? null : byMaterial.get(material);
    }
}
