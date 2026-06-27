package io.github.term4.minestommechanics.mechanics.attribute.source;

import net.kyori.adventure.key.Key;

/**
 * An item-borne source - an enchant. Matched against an in-context item's enchants (level = enchant level), read on
 * demand during a calculation (not pushed); its state rides the item (components/NBT), not the entity.
 */
public abstract class ItemSource extends Source {
    protected ItemSource(Key key) { super(key); }
}
