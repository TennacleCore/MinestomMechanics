package io.github.term4.minestommechanics.mechanics.attribute.catalog.enchant;

import net.kyori.adventure.key.Key;

/**
 * Power (enchant) - more arrow damage. A projectile-domain enchant, not a {@link
 * io.github.term4.minestommechanics.mechanics.attribute.source.Source}: the projectile captures its level off the launching
 * item and the arrow's hit damage adds {@code 0.5 × level + 0.5} to the per-velocity damage (vanilla 1.8 + 26). The
 * library captures it generically off any launcher, so a preset can put Power on non-arrow projectiles. Identity key only.
 */
public final class Power {
    public static final Key KEY = Key.key("minecraft:power");
    private Power() {}
}
