package io.github.term4.minestommechanics.mechanics.attribute.catalog;

import net.minestom.server.potion.CustomPotionEffect;
import net.minestom.server.potion.PotionEffect;
import net.minestom.server.potion.PotionType;

import java.util.List;
import java.util.Map;

/**
 * Vanilla base-potion → effects table - a mirror of vanilla's {@code minecraft:potion} registry (26 {@code Potions.java}),
 * needed because Minestom's {@link PotionType} is only a key and doesn't carry its effects. Consumers resolve a potion
 * item's base {@code potion} through this (the tipped-arrow framework, the drinkable-potion consumable); a potion's own
 * {@code customEffects} are used directly and don't need a row.
 *
 * <p><b>Version-invariant.</b> The recipe (which effect + duration/amplifier) is the same in 1.8 and 26 for every shared
 * potion (Swiftness 3600/9600/1800, Regeneration 900/1800/450, Poison 900/1800/432, …); what differs by version is each
 * <em>effect's mechanic</em>, which lives in the {@code LEGACY}/{@code MODERN} effect sources, not here. The 26-only
 * potions (Turtle Master, Slow Falling, Luck, Strong Slowness) are simply never reachable by a 1.8/Via client, so one
 * table serves both. The vanilla 1/8 tipped-arrow scaling is NOT baked here - it's the arrow's {@code potion_duration_scale}.
 */
public final class VanillaPotions {
    private VanillaPotions() {}

    /** {@code amplifier} is 0-based (level I = 0); instant potions use duration {@code 1}. Ambient off, particles + icon on. */
    private static CustomPotionEffect effect(PotionEffect id, int amplifier, int duration) {
        return new CustomPotionEffect(id, amplifier, duration, false, true, true);
    }

    private static final Map<PotionType, List<CustomPotionEffect>> TABLE = Map.ofEntries(
            Map.entry(PotionType.NIGHT_VISION, List.of(effect(PotionEffect.NIGHT_VISION, 0, 3600))),
            Map.entry(PotionType.LONG_NIGHT_VISION, List.of(effect(PotionEffect.NIGHT_VISION, 0, 9600))),
            Map.entry(PotionType.INVISIBILITY, List.of(effect(PotionEffect.INVISIBILITY, 0, 3600))),
            Map.entry(PotionType.LONG_INVISIBILITY, List.of(effect(PotionEffect.INVISIBILITY, 0, 9600))),
            Map.entry(PotionType.LEAPING, List.of(effect(PotionEffect.JUMP_BOOST, 0, 3600))),
            Map.entry(PotionType.LONG_LEAPING, List.of(effect(PotionEffect.JUMP_BOOST, 0, 9600))),
            Map.entry(PotionType.STRONG_LEAPING, List.of(effect(PotionEffect.JUMP_BOOST, 1, 1800))),
            Map.entry(PotionType.FIRE_RESISTANCE, List.of(effect(PotionEffect.FIRE_RESISTANCE, 0, 3600))),
            Map.entry(PotionType.LONG_FIRE_RESISTANCE, List.of(effect(PotionEffect.FIRE_RESISTANCE, 0, 9600))),
            Map.entry(PotionType.SWIFTNESS, List.of(effect(PotionEffect.SPEED, 0, 3600))),
            Map.entry(PotionType.LONG_SWIFTNESS, List.of(effect(PotionEffect.SPEED, 0, 9600))),
            Map.entry(PotionType.STRONG_SWIFTNESS, List.of(effect(PotionEffect.SPEED, 1, 1800))),
            Map.entry(PotionType.SLOWNESS, List.of(effect(PotionEffect.SLOWNESS, 0, 1800))),
            Map.entry(PotionType.LONG_SLOWNESS, List.of(effect(PotionEffect.SLOWNESS, 0, 4800))),
            Map.entry(PotionType.STRONG_SLOWNESS, List.of(effect(PotionEffect.SLOWNESS, 3, 400))),
            Map.entry(PotionType.TURTLE_MASTER, List.of(effect(PotionEffect.SLOWNESS, 3, 400), effect(PotionEffect.RESISTANCE, 2, 400))),
            Map.entry(PotionType.LONG_TURTLE_MASTER, List.of(effect(PotionEffect.SLOWNESS, 3, 800), effect(PotionEffect.RESISTANCE, 2, 800))),
            Map.entry(PotionType.STRONG_TURTLE_MASTER, List.of(effect(PotionEffect.SLOWNESS, 5, 400), effect(PotionEffect.RESISTANCE, 3, 400))),
            Map.entry(PotionType.WATER_BREATHING, List.of(effect(PotionEffect.WATER_BREATHING, 0, 3600))),
            Map.entry(PotionType.LONG_WATER_BREATHING, List.of(effect(PotionEffect.WATER_BREATHING, 0, 9600))),
            Map.entry(PotionType.HEALING, List.of(effect(PotionEffect.INSTANT_HEALTH, 0, 1))),
            Map.entry(PotionType.STRONG_HEALING, List.of(effect(PotionEffect.INSTANT_HEALTH, 1, 1))),
            Map.entry(PotionType.HARMING, List.of(effect(PotionEffect.INSTANT_DAMAGE, 0, 1))),
            Map.entry(PotionType.STRONG_HARMING, List.of(effect(PotionEffect.INSTANT_DAMAGE, 1, 1))),
            Map.entry(PotionType.POISON, List.of(effect(PotionEffect.POISON, 0, 900))),
            Map.entry(PotionType.LONG_POISON, List.of(effect(PotionEffect.POISON, 0, 1800))),
            Map.entry(PotionType.STRONG_POISON, List.of(effect(PotionEffect.POISON, 1, 432))),
            Map.entry(PotionType.REGENERATION, List.of(effect(PotionEffect.REGENERATION, 0, 900))),
            Map.entry(PotionType.LONG_REGENERATION, List.of(effect(PotionEffect.REGENERATION, 0, 1800))),
            Map.entry(PotionType.STRONG_REGENERATION, List.of(effect(PotionEffect.REGENERATION, 1, 450))),
            Map.entry(PotionType.STRENGTH, List.of(effect(PotionEffect.STRENGTH, 0, 3600))),
            Map.entry(PotionType.LONG_STRENGTH, List.of(effect(PotionEffect.STRENGTH, 0, 9600))),
            Map.entry(PotionType.STRONG_STRENGTH, List.of(effect(PotionEffect.STRENGTH, 1, 1800))),
            Map.entry(PotionType.WEAKNESS, List.of(effect(PotionEffect.WEAKNESS, 0, 1800))),
            Map.entry(PotionType.LONG_WEAKNESS, List.of(effect(PotionEffect.WEAKNESS, 0, 4800))),
            Map.entry(PotionType.LUCK, List.of(effect(PotionEffect.LUCK, 0, 6000))),
            Map.entry(PotionType.SLOW_FALLING, List.of(effect(PotionEffect.SLOW_FALLING, 0, 1800))),
            Map.entry(PotionType.LONG_SLOW_FALLING, List.of(effect(PotionEffect.SLOW_FALLING, 0, 4800)))
    );

    /** The concrete effects for a vanilla base potion type, or empty for an effectless base (water / mundane / thick / awkward) or an unmapped type. */
    public static List<CustomPotionEffect> effects(PotionType potion) {
        return TABLE.getOrDefault(potion, List.of());
    }
}
