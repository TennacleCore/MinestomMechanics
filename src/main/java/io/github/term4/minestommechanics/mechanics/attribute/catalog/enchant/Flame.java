package io.github.term4.minestommechanics.mechanics.attribute.catalog.enchant;

import net.kyori.adventure.key.Key;

/**
 * Flame (enchant) - a bow enchant that ignites the struck entity. Vanilla (1.8 + 26 identical): a Flame arrow flies alight
 * and sets a hit entity on fire for 5 seconds ({@code EntityArrow}: {@code if (isBurning()) entityHit.setOnFire(5)}); max
 * level 1, so the duration is fixed, not level-scaled. The burn itself is the existing {@code on_fire} damage type once the
 * victim is alight. A projectile-domain enchant captured off the launcher (like {@link Power}/{@link Punch}), not a
 * {@link io.github.term4.minestommechanics.mechanics.attribute.source.Source}. Identity key only - 1.8 and 26 are the same.
 */
public final class Flame {
    public static final Key KEY = Key.key("minecraft:flame");
    /** Fire ticks a Flame arrow applies to a struck entity: 5 seconds × 20. */
    public static final int FIRE_TICKS = 5 * 20;
    private Flame() {}
}
