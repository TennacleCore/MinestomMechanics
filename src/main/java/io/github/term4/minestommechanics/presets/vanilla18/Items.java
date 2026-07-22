package io.github.term4.minestommechanics.presets.vanilla18;

import io.github.term4.minestommechanics.item.ItemDef;
import io.github.term4.minestommechanics.item.ItemRegistry;
import io.github.term4.minestommechanics.item.VanillaItems;

/** Vanilla 1.8 item registry: the LEGACY weapon table; armor rides Minestom's {@code ARMOR} attribute. */
public final class Items {

    private Items() {}

    public static ItemRegistry registry() {
        return new ItemRegistry(ItemDef.Version.LEGACY, VanillaItems.weapons());
    }
}
