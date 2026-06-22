package io.github.term4.minestommechanics.mechanics.attribute.catalog.enchant;

import net.kyori.adventure.key.Key;

/**
 * Knockback (enchant) - a melee knockback-strength bonus. A knockback-domain enchant, so it is <em>not</em> a
 * {@link io.github.term4.minestommechanics.mechanics.attribute.source.Source} (no attribute modifier): the
 * {@code KnockbackCalculator} reads its level off the attacker's weapon and folds it into the extra-knockback level
 * alongside the sprint bonus (vanilla {@code i = knockbackLevel + (sprinting ? 1 : 0)}, each level worth the configured
 * extra horizontal). Identity key only - 1.8 and 26 are the same.
 */
public final class Knockback {
    public static final Key KEY = Key.key("minecraft:knockback");
    private Knockback() {}
}
