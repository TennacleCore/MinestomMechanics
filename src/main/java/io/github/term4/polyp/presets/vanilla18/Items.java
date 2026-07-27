package io.github.term4.polyp.presets.vanilla18;

import io.github.term4.polyp.item.ItemDef;
import io.github.term4.polyp.item.ItemRegistry;
import io.github.term4.polyp.item.VanillaItems;

/** Vanilla 1.8 item registry: the LEGACY weapon table; armor rides Minestom's {@code ARMOR} attribute. */
public final class Items {

    private Items() {}

    public static ItemRegistry registry() {
        return new ItemRegistry(ItemDef.Version.LEGACY, VanillaItems.weapons());
    }
}
