package io.github.term4.minestommechanics.mechanics.attribute.catalog.enchant;

import io.github.term4.minestommechanics.mechanics.attribute.Attribute;
import io.github.term4.minestommechanics.mechanics.attribute.source.HeldSource;
import io.github.term4.minestommechanics.mechanics.attribute.source.Source;
import net.kyori.adventure.key.Key;
import net.minestom.server.entity.attribute.AttributeOperation;

import java.util.List;

/**
 * Efficiency (enchant) - {@code level² + 1} mining speed on {@link Attribute#MINING_EFFICIENCY} ({@code ADD_VALUE}).
 * Identical in 1.8 ({@code i² + 1} added in the dig formula, {@code EntityHuman}) and 26 ({@code mining_efficiency} via
 * the same {@code levels_squared + 1}), so one variant. A {@link HeldSource}: pushed onto the holder's
 * {@code MINING_EFFICIENCY} while the tool is in the main hand, since mining speed is client-computed (and Minestom's
 * {@code BlockBreakCalculation} reads the attribute) - a modern client needs the attribute on the entity; 1.8 clients
 * read the enchant off the item themselves. The "only when the tool is effective" gate lives in the dig calculator.
 */
public final class Efficiency {

    public static final Key KEY = Key.key("minecraft:efficiency");

    private Efficiency() {}

    public static final Source INSTANCE = new HeldSource(KEY) {
        @Override public List<Mod> modifiers(int level) {
            return level <= 0 ? List.of()
                    : List.of(new Mod(Attribute.MINING_EFFICIENCY, AttributeOperation.ADD_VALUE, level * level + 1));
        }
    };
}
