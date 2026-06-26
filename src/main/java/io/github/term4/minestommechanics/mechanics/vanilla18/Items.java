package io.github.term4.minestommechanics.mechanics.vanilla18;

import io.github.term4.minestommechanics.item.ItemDef;
import io.github.term4.minestommechanics.item.ItemRegistry;
import io.github.term4.minestommechanics.item.VanillaItems;

/**
 * Vanilla 1.8 item registry: the LEGACY weapon table; armor rides Minestom's {@code ARMOR} attribute. Registered
 * separately via {@code mm.registerItems(...)} - it is not a {@code MechanicsProfile} member, so the {@link Vanilla18}
 * preset does not carry it.
 */
public final class Items {

    private Items() {}

    /** The vanilla 1.8 item registry (LEGACY weapon stats). */
    public static ItemRegistry registry() {
        return new ItemRegistry(ItemDef.Version.LEGACY, VanillaItems.weapons());
    }
}
