package io.github.term4.minestommechanics.mechanics.attribute.source;

import net.kyori.adventure.key.Key;

/**
 * A held-item source - an enchant pushed onto the holder while its item is in the main hand (Efficiency ->
 * {@code MINING_EFFICIENCY}), reconciled when the held item or hotbar slot changes. Unlike {@link ItemSource} (read on
 * demand during a combat calc, never pushed), the attribute must be on the holder because mining is client-computed - so
 * this only matters for modern clients (1.8 reads the enchant off the item itself).
 */
public abstract class HeldSource extends Source {
    protected HeldSource(Key key) { super(key); }
}
