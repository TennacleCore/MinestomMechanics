package io.github.term4.minestommechanics.mechanics.attribute.source;

import net.kyori.adventure.key.Key;

/**
 * An entity-borne source - a potion effect. Matched against the entity's active effects (level = amplifier + 1); owns the
 * potion lifecycle: modifiers pushed/removed on the entity's Minestom instances, {@link Behavior} fires on apply/remove/tick.
 */
public abstract class EntitySource extends Source {
    protected EntitySource(Key key) { super(key); }
}
