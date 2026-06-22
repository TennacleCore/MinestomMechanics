package io.github.term4.minestommechanics.mechanics.attribute.source;

import net.kyori.adventure.key.Key;

/**
 * A worn-equipment source - an enchant active while its armor piece is equipped (Aqua Affinity, Depth Strider, Frost
 * Walker, a custom fire-walker, ...). The third {@link Source} kind alongside {@link EntitySource} (potions) and
 * {@link ItemSource} (held item): its modifiers are pushed/cleared on the equipment lifecycle (mirroring the potion one)
 * and its {@link Behavior} fires on equip/unequip and ticks while worn. Level = the highest level across the four armor
 * pieces (these are single-slot enchants). The "armor attributes/behaviors" half.
 */
public abstract class ArmorSource extends Source {
    protected ArmorSource(Key key) { super(key); }
}
