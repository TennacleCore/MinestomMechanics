package io.github.term4.minestommechanics.mechanics.vanilla;

import io.github.term4.minestommechanics.mechanics.consumable.ConsumableConfig;
import io.github.term4.minestommechanics.mechanics.consumable.ConsumableTypeConfig;
import io.github.term4.minestommechanics.mechanics.consumable.catalog.VanillaConsumables;
import net.minestom.server.potion.PotionEffect;

/**
 * Modern (26) consumables: the golden apples with 26-source effects. Regular = Regen II (5s) + Absorption I (2m);
 * enchanted = Regen II (20s) + Resistance (5m) + Fire Resistance (5m) + Absorption IV (2m). Both restore 4 food + 9.6
 * saturation. Differs from {@code vanilla18.Consumables} (1.8: enchanted is Regen V + Absorption I).
 */
public final class Consumables {

    private Consumables() {}

    /** ConsumableConfig with modern (26) golden-apple effects. */
    public static ConsumableConfig config() {
        return ConsumableConfig.builder()
                .typeConfigs(
                        ConsumableTypeConfig.builder(VanillaConsumables.GOLDEN_APPLE.key())
                                .behavior(VanillaConsumables.effectFood(4, 9.6f,
                                        VanillaConsumables.eff(PotionEffect.REGENERATION, 2, 100),
                                        VanillaConsumables.eff(PotionEffect.ABSORPTION, 1, 2400)))
                                .build(),
                        ConsumableTypeConfig.builder(VanillaConsumables.ENCHANTED_GOLDEN_APPLE.key())
                                .behavior(VanillaConsumables.effectFood(4, 9.6f,
                                        VanillaConsumables.eff(PotionEffect.REGENERATION, 2, 400),
                                        VanillaConsumables.eff(PotionEffect.RESISTANCE, 1, 6000),
                                        VanillaConsumables.eff(PotionEffect.FIRE_RESISTANCE, 1, 6000),
                                        VanillaConsumables.eff(PotionEffect.ABSORPTION, 4, 2400)))
                                .build())
                .types(VanillaConsumables.types()) // the golden-apple type identities (key + material)
                .build();
    }
}
