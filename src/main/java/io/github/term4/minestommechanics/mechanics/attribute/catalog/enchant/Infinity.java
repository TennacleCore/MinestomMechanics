package io.github.term4.minestommechanics.mechanics.attribute.catalog.enchant;

import net.kyori.adventure.key.Key;

/**
 * Infinity (enchant) - the bow does not consume arrows (still needs at least one in the quiver), and the arrows it fires
 * can't be picked up in survival. A bow-domain enchant, not a {@link io.github.term4.minestommechanics.mechanics.attribute.source.Source};
 * {@code Bow} reads its level off the held bow. Identity key only - 1.8 and 26 are the same.
 */
public final class Infinity {
    public static final Key KEY = Key.key("minecraft:infinity");
    private Infinity() {}
}
