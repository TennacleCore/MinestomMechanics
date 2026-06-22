package io.github.term4.minestommechanics.mechanics.attribute.source;

import net.kyori.adventure.key.Key;

/**
 * An item-borne source - an enchant (and later other item attributes). Matched against an in-context item's enchants
 * (level = enchant level) and read on demand during a calculation (not pushed via the potion lifecycle); its state
 * rides the item (components/NBT), not the entity. The "item attributes" half.
 */
public abstract class ItemSource extends Source {
    protected ItemSource(Key key) { super(key); }
}
