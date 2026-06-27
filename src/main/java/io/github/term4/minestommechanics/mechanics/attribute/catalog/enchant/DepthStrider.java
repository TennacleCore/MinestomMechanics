package io.github.term4.minestommechanics.mechanics.attribute.catalog.enchant;

import io.github.term4.minestommechanics.mechanics.attribute.source.ArmorSource;
import io.github.term4.minestommechanics.mechanics.attribute.Attribute;
import io.github.term4.minestommechanics.mechanics.attribute.source.Source;
import net.kyori.adventure.key.Key;
import net.minestom.server.entity.attribute.AttributeOperation;

import java.util.List;

/**
 * Depth Strider (enchant) - faster movement through water. An {@link ArmorSource} (worn-boots enchant), pushed onto the
 * wearer's {@code WATER_MOVEMENT_EFFICIENCY} while equipped: {@code +1/3 × level} {@code ADD_VALUE} (26's
 * {@code water_movement_efficiency} data effect; full at level 3). Consumed by the wearer's movement (Minestom physics / the client), so no separate calc.
 */
public final class DepthStrider {

    public static final Key KEY = Key.key("minecraft:depth_strider");

    private DepthStrider() {}

    public static final Source INSTANCE = new ArmorSource(KEY) {
        @Override public List<Mod> modifiers(int level) {
            return level <= 0 ? List.of()
                    : List.of(new Mod(Attribute.WATER_MOVEMENT_EFFICIENCY, AttributeOperation.ADD_VALUE, level / 3.0));
        }
    };
}
