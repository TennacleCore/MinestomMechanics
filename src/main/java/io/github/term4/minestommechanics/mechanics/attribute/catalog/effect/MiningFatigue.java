package io.github.term4.minestommechanics.mechanics.attribute.catalog.effect;

import io.github.term4.minestommechanics.mechanics.attribute.Attribute;
import io.github.term4.minestommechanics.mechanics.attribute.source.EntitySource;
import io.github.term4.minestommechanics.mechanics.attribute.source.Source;
import net.kyori.adventure.key.Key;
import net.minestom.server.entity.attribute.AttributeOperation;

import java.util.List;

/**
 * Mining Fatigue (potion). {@link #MODERN} is {@code -0.1 × level} attack-speed ({@code ADD_MULTIPLIED_TOTAL}, 26). 1.8 is
 * a dig-speed mechanic (no attribute) - needs the mining system, so no LEGACY variant yet.
 */
public final class MiningFatigue {

    public static final Key KEY = Key.key("minecraft:mining_fatigue");

    private MiningFatigue() {}

    public static final Source MODERN = new EntitySource(KEY) {
        @Override public List<Mod> modifiers(int level) {
            return level <= 0 ? List.of()
                    : List.of(new Mod(Attribute.ATTACK_SPEED, AttributeOperation.ADD_MULTIPLIED_TOTAL, -0.1 * level));
        }
    };
}
