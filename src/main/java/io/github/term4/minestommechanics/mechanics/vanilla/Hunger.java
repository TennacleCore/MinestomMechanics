package io.github.term4.minestommechanics.mechanics.vanilla;

import io.github.term4.minestommechanics.mechanics.hunger.ExhaustionCost;
import io.github.term4.minestommechanics.mechanics.hunger.HungerConfig;
import io.github.term4.minestommechanics.mechanics.hunger.HungerSystem;

/**
 * Modern hunger ({@code FoodData.tick}): saturation fast regen at food 20 (heal {@code min(sat,6)/6} every 10 ticks,
 * exhaustion = the spent saturation), else 1 per 80 ticks at food 18+ with 6.0 exhaustion.
 */
public final class Hunger {

    private Hunger() {}

    public static HungerConfig config() {
        return HungerConfig.builder()
                .naturalRegen(true)
                .regenFoodThreshold(18)
                .regenInterval(80)
                .exhaustionCost(HungerSystem.REGEN_COST, ExhaustionCost.flat(6.0f))
                .exhaustionCost(HungerSystem.SATURATION_REGEN_COST, ExhaustionCost.dynamic())
                .saturationRegen(true)
                .build();
    }
}
