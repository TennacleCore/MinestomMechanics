package io.github.term4.minestommechanics.presets.vanilla;

import io.github.term4.minestommechanics.item.ItemDef;
import io.github.term4.minestommechanics.item.ItemRegistry;
import io.github.term4.minestommechanics.item.VanillaItems;

/** Modern (26) item registry: weapon attack damage derives from Minestom's {@code ATTACK_DAMAGE}. */
public final class Items {

    private Items() {}

    public static ItemRegistry registry() {
        return new ItemRegistry(ItemDef.Version.MODERN, VanillaItems.weapons());
    }
}
