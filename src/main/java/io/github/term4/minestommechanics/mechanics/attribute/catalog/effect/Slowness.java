package io.github.term4.minestommechanics.mechanics.attribute.catalog.effect;

import io.github.term4.minestommechanics.mechanics.attribute.Attribute;
import io.github.term4.minestommechanics.mechanics.attribute.source.EntitySource;
import io.github.term4.minestommechanics.mechanics.attribute.source.Source;
import net.kyori.adventure.key.Key;
import net.minestom.server.entity.attribute.AttributeOperation;

import java.util.List;

/**
 * Slowness (potion) - {@code -0.15 × level} movement-speed {@code ADD_MULTIPLIED_TOTAL}. Identical in 1.8 and 26, so one
 * variant. The mirror of {@link Speed}; also the effect Bane of Arthropods applies on an arthropod hit.
 */
public final class Slowness {

    public static final Key KEY = Key.key("minecraft:slowness");

    private Slowness() {}

    public static final Source INSTANCE = new EntitySource(KEY) {
        @Override public List<Mod> modifiers(int level) {
            return level <= 0 ? List.of()
                    : List.of(new Mod(Attribute.MOVEMENT_SPEED, AttributeOperation.ADD_MULTIPLIED_TOTAL, -0.15 * level));
        }
    };
}
