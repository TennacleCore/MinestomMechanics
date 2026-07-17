package io.github.term4.minestommechanics.mechanics.vanilla;

import io.github.term4.minestommechanics.mechanics.consumable.ComponentFood;
import io.github.term4.minestommechanics.mechanics.consumable.ConsumableConfig;
import io.github.term4.minestommechanics.mechanics.consumable.ConsumableTypeConfig;
import io.github.term4.minestommechanics.mechanics.consumable.catalog.VanillaConsumables;
import net.kyori.adventure.key.Key;
import net.minestom.server.potion.PotionEffect;

/**
 * Modern (26) consumables: the golden apples with 26-source effects (regular = Regen II 5s + Absorption I 2m;
 * enchanted = Regen II 20s + Resistance 5m + Fire Resistance 5m + Absorption IV 2m; both 4 food + 9.6 saturation -
 * 1.8's enchanted is Regen V + Absorption I), the modern pufferfish (Poison II - 1.8 is IV), and the modern
 * {@code canEat} gate (creative always eats, no item loss) on every food incl. the component floor.
 */
public final class Consumables {

    private Consumables() {}

    public static ConsumableConfig config() {
        return ConsumableConfig.builder()
                .typeConfigs(
                        ConsumableTypeConfig.builder(VanillaConsumables.GOLDEN_APPLE.key())
                                .canConsume(ctx -> VanillaConsumables.modernCanEat(ctx, true)) // 26 lets creative eat (no item loss)
                                .behavior(VanillaConsumables.effectFood(4, 9.6f,
                                        VanillaConsumables.eff(PotionEffect.REGENERATION, 2, 100),
                                        VanillaConsumables.eff(PotionEffect.ABSORPTION, 1, 2400)))
                                .build(),
                        ConsumableTypeConfig.builder(VanillaConsumables.ENCHANTED_GOLDEN_APPLE.key())
                                .canConsume(ctx -> VanillaConsumables.modernCanEat(ctx, true))
                                .behavior(VanillaConsumables.effectFood(4, 9.6f,
                                        VanillaConsumables.eff(PotionEffect.REGENERATION, 2, 400),
                                        VanillaConsumables.eff(PotionEffect.RESISTANCE, 1, 6000),
                                        VanillaConsumables.eff(PotionEffect.FIRE_RESISTANCE, 1, 6000),
                                        VanillaConsumables.eff(PotionEffect.ABSORPTION, 4, 2400)))
                                .build(),
                        food(VanillaConsumables.RAW_CHICKEN.key()),
                        food(VanillaConsumables.ROTTEN_FLESH.key()),
                        food(VanillaConsumables.SPIDER_EYE.key()),
                        food(VanillaConsumables.POISONOUS_POTATO.key()),
                        ConsumableTypeConfig.builder(VanillaConsumables.PUFFERFISH.key())
                                .canConsume(ctx -> VanillaConsumables.modernCanEat(ctx, false))
                                .behavior(VanillaConsumables.effectFood(1, 0.2f,
                                        VanillaConsumables.eff(PotionEffect.POISON, 2, 1200),
                                        VanillaConsumables.eff(PotionEffect.HUNGER, 3, 300),
                                        VanillaConsumables.eff(PotionEffect.NAUSEA, 1, 300)))
                                .build(),
                        ConsumableTypeConfig.builder(ComponentFood.KEY)
                                .canConsume(ctx -> VanillaConsumables.modernCanEat(ctx, ComponentFood.alwaysEdible(ctx.item())))
                                .build())
                .types(VanillaConsumables.types())
                .build();
    }

    /** The modern eat gate for a food whose behavior lives on its type. */
    private static ConsumableTypeConfig food(Key key) {
        return ConsumableTypeConfig.builder(key).canConsume(ctx -> VanillaConsumables.modernCanEat(ctx, false)).build();
    }
}
