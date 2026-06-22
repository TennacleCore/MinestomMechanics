package io.github.term4.minestommechanics.mechanics.attribute.source;

import net.kyori.adventure.key.Key;

/**
 * A held-item source - an enchant pushed onto the holder while its item is in the main hand (Efficiency ->
 * {@code MINING_EFFICIENCY}). The fourth {@link Source} kind: like {@link ArmorSource} but for the main-hand slot,
 * reconciled when the held item or selected hotbar slot changes. Distinct from {@link ItemSource} (Sharpness/Smite),
 * which is read on demand during a server calc (combat) and never pushed - mining is client-computed, so its attribute
 * must actually be on the holder for a modern client (and for Minestom's {@code BlockBreakCalculation}). 1.8 clients read
 * the enchant off the item themselves, so this only matters for modern clients.
 */
public abstract class HeldSource extends Source {
    protected HeldSource(Key key) { super(key); }
}
