package io.github.term4.minestommechanics.mechanics.attribute.source;

import net.kyori.adventure.key.Key;

/**
 * A worn-equipment source - an enchant active while its armor piece is equipped (e.g. Aqua Affinity, Frost Walker).
 * Modifiers pushed/cleared on the equipment lifecycle (like the potion one); {@link Behavior} fires on equip/unequip and
 * ticks while worn. Level = highest across the four armor pieces (single-slot enchants).
 */
public abstract class ArmorSource extends Source {
    protected ArmorSource(Key key) { super(key); }
}
