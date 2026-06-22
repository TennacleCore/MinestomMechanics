package io.github.term4.minestommechanics.mechanics.attribute.catalog.enchant;

import io.github.term4.minestommechanics.mechanics.attribute.source.ArmorSource;
import io.github.term4.minestommechanics.mechanics.attribute.Attribute;
import io.github.term4.minestommechanics.mechanics.attribute.source.Source;
import net.kyori.adventure.key.Key;
import net.minestom.server.entity.attribute.AttributeOperation;

import java.util.List;

/**
 * Aqua Affinity (enchant) - faster mining while submerged. An {@link ArmorSource} (worn-helmet enchant), pushed onto the
 * wearer's {@code SUBMERGED_MINING_SPEED} while equipped: 26's {@code +4 × level} {@code ADD_MULTIPLIED_TOTAL}
 * ({@code submerged_mining_speed} data effect). Like {@link Efficiency}, this is the <em>delivery</em> half - it is inert
 * until a dig/mining calculator consumes the attribute (1.8 instead removes the underwater 5x penalty in that calculator).
 */
public final class AquaAffinity {

    public static final Key KEY = Key.key("minecraft:aqua_affinity");

    private AquaAffinity() {}

    public static final Source INSTANCE = new ArmorSource(KEY) {
        @Override public List<Mod> modifiers(int level) {
            return level <= 0 ? List.of()
                    : List.of(new Mod(Attribute.SUBMERGED_MINING_SPEED, AttributeOperation.ADD_MULTIPLIED_TOTAL, 4.0 * level));
        }
    };
}
