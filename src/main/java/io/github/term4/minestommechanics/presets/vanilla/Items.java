package io.github.term4.minestommechanics.presets.vanilla;

import io.github.term4.minestommechanics.item.ItemDef;
import io.github.term4.minestommechanics.item.ItemRegistry;
import io.github.term4.minestommechanics.item.VanillaItems;

/**
 * Modern (26) item registry: the MODERN version - weapon attack damage derives from Minestom's {@code ATTACK_DAMAGE}.
 * Carried on the profile as the {@code MechanicsKeys.ITEMS} member ({@link Vanilla#profile()} sets it).
 */
public final class Items {

    private Items() {}

    public static ItemRegistry registry() {
        return new ItemRegistry(ItemDef.Version.MODERN, VanillaItems.weapons());
    }
}
