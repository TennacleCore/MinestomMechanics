package io.github.term4.minestommechanics.mechanics.attribute.catalog.effect;

import io.github.term4.minestommechanics.mechanics.attribute.Attribute;
import io.github.term4.minestommechanics.mechanics.attribute.source.EntitySource;
import io.github.term4.minestommechanics.mechanics.attribute.source.Source;
import net.kyori.adventure.key.Key;
import net.minestom.server.entity.attribute.AttributeOperation;

import java.util.List;

/**
 * Jump Boost (potion). {@link #MODERN} is {@code +1 × level} safe-fall-distance ({@code ADD_VALUE}, 26) - the
 * fall-damage-reduction part. The jump-velocity part ({@code +0.1/level}, both versions) isn't an attribute -
 * {@link io.github.term4.minestommechanics.tracking.motion.MotionTracker#jumpSeed} seeds it off the live effect, so no LEGACY source here.
 */
public final class JumpBoost {

    public static final Key KEY = Key.key("minecraft:jump_boost");

    private JumpBoost() {}

    public static final Source MODERN = new EntitySource(KEY) {
        @Override public List<Mod> modifiers(int level) {
            return level <= 0 ? List.of()
                    : List.of(new Mod(Attribute.SAFE_FALL_DISTANCE, AttributeOperation.ADD_VALUE, 1.0 * level));
        }
    };
}
