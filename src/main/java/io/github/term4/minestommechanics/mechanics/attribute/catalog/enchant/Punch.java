package io.github.term4.minestommechanics.mechanics.attribute.catalog.enchant;

import net.kyori.adventure.key.Key;

/**
 * Punch (enchant) - extra arrow knockback. A projectile-domain enchant, not a {@link
 * io.github.term4.minestommechanics.mechanics.attribute.source.Source}: the projectile captures its level off the launching item
 * and feeds it as the extra-knockback level on the hit (a separate motion-direction knockback, vanilla {@code 0.6 × level}
 * horizontal). Captured generically off any launcher. Identity key only - 1.8 and 26 are the same.
 */
public final class Punch {
    public static final Key KEY = Key.key("minecraft:punch");
    private Punch() {}
}
