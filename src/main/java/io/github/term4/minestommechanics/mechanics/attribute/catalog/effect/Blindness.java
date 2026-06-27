package io.github.term4.minestommechanics.mechanics.attribute.catalog.effect;

import io.github.term4.minestommechanics.mechanics.attribute.source.EntitySource;
import io.github.term4.minestommechanics.mechanics.attribute.source.Source;
import net.kyori.adventure.key.Key;

/**
 * Blindness (potion) - no modifier or server behavior; a purely client-rendered effect (reduced render distance + black
 * vignette) the client applies off the stored effect. The no-op source is registered to be a discoverable,
 * scope-disableable catalog entry. Version-agnostic.
 */
public final class Blindness {

    public static final Key KEY = Key.key("minecraft:blindness");

    private Blindness() {}

    public static final Source INSTANCE = new EntitySource(KEY) {};
}
