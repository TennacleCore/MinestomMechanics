package io.github.term4.minestommechanics.mechanics.vanilla18;

import io.github.term4.minestommechanics.mechanics.consumable.ConsumableConfig;
import io.github.term4.minestommechanics.mechanics.consumable.ConsumableTypeConfig;
import io.github.term4.minestommechanics.mechanics.consumable.catalog.VanillaConsumables;
import net.minestom.server.potion.PotionEffect;

/**
 * Vanilla 1.8 consumables: the golden apples, with 1.8-source effects. Regular = Regen II (5s) + Absorption I (2m);
 * enchanted ("notch") = Regen V (30s) + Resistance (5m) + Fire Resistance (5m) + Absorption I (2m). Both restore 4 food +
 * 9.6 saturation. Differs from the modern preset (26: enchanted is Regen II + Absorption IV).
 */
public final class Consumables {

    private Consumables() {}

    /** ConsumableConfig with vanilla 1.8 golden-apple effects (the registered {@link VanillaConsumables#types() types}). */
    public static ConsumableConfig config() {
        return ConsumableConfig.builder()
                .typeConfigs(
                        ConsumableTypeConfig.builder(VanillaConsumables.GOLDEN_APPLE.key())
                                .canConsume(ctx -> VanillaConsumables.legacyCanEat(ctx, true)) // golden apples are always-edible; 1.8 still blocks creative
                                .behavior(VanillaConsumables.effectFood(4, 9.6f,
                                        VanillaConsumables.eff(PotionEffect.REGENERATION, 2, 100),
                                        VanillaConsumables.eff(PotionEffect.ABSORPTION, 1, 2400)))
                                .build(),
                        ConsumableTypeConfig.builder(VanillaConsumables.ENCHANTED_GOLDEN_APPLE.key())
                                .canConsume(ctx -> VanillaConsumables.legacyCanEat(ctx, true))
                                .behavior(VanillaConsumables.effectFood(4, 9.6f,
                                        VanillaConsumables.eff(PotionEffect.REGENERATION, 5, 600),
                                        VanillaConsumables.eff(PotionEffect.RESISTANCE, 1, 6000),
                                        VanillaConsumables.eff(PotionEffect.FIRE_RESISTANCE, 1, 6000),
                                        VanillaConsumables.eff(PotionEffect.ABSORPTION, 1, 2400)))
                                .build())
                .types(VanillaConsumables.types()) // the golden-apple type identities (key + material)
                .build();
    }
}
