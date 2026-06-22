package io.github.term4.minestommechanics.mechanics.attribute.source;

import net.kyori.adventure.key.Key;

/**
 * An entity-borne source - a potion effect (and later beacon/other entity effects). Matched against the entity's active
 * potion effects (level = amplifier + 1), and it owns the potion lifecycle: its modifiers are pushed/removed on the
 * entity's Minestom instances and its {@link Behavior} fires on apply/remove/tick. The "player attributes" half.
 */
public abstract class EntitySource extends Source {
    protected EntitySource(Key key) { super(key); }
}
