package io.github.term4.minestommechanics.mechanics.attribute.catalog.enchant;

import net.kyori.adventure.key.Key;

/**
 * Flame (enchant) - a bow enchant that ignites the struck entity for 5 seconds (vanilla 1.8 + 26 identical;
 * {@code EntityArrow} {@code setOnFire(5)}); max level 1, so fixed duration. A projectile-domain enchant captured off the
 * launcher (like {@link Power}/{@link Punch}), not a {@link io.github.term4.minestommechanics.mechanics.attribute.source.Source}. Identity key only.
 */
public final class Flame {
    public static final Key KEY = Key.key("minecraft:flame");
    /** Fire ticks a Flame arrow applies to a struck entity: 5 seconds × 20. */
    public static final int FIRE_TICKS = 5 * 20;
    private Flame() {}
}
