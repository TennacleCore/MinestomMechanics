package io.github.term4.minestommechanics.mechanics.attribute.catalog.effect;

import io.github.term4.minestommechanics.mechanics.attribute.source.EntitySource;
import io.github.term4.minestommechanics.mechanics.attribute.source.Source;
import net.kyori.adventure.key.Key;

/**
 * Night Vision (potion) - no attribute modifier and no server behavior; a purely client-rendered effect (full brightness +
 * the near-expiry flicker). The client applies it off the stored effect, so the source is a no-op - registered only so it's
 * a first-class, discoverable catalog effect (scope-disableable like any other). Version-agnostic.
 */
public final class NightVision {

    public static final Key KEY = Key.key("minecraft:night_vision");

    private NightVision() {}

    public static final Source INSTANCE = new EntitySource(KEY) {};
}
