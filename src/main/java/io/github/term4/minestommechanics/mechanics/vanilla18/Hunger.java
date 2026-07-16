package io.github.term4.minestommechanics.mechanics.vanilla18;

import io.github.term4.minestommechanics.mechanics.hunger.ExhaustionCost;
import io.github.term4.minestommechanics.mechanics.hunger.HungerConfig;
import io.github.term4.minestommechanics.mechanics.hunger.HungerSystem;

/**
 * 1.8 hunger ({@code FoodMetaData.a}): heal 1 per 80 ticks at food 18+, 3.0 exhaustion per heal, NO saturation fast
 * regen (that's 1.9+).
 */
public final class Hunger {

    private Hunger() {}

    public static HungerConfig config() {
        return HungerConfig.builder()
                .naturalRegen(true)
                .regenFoodThreshold(18)
                .regenInterval(80)
                .exhaustionCost(HungerSystem.REGEN_COST, ExhaustionCost.flat(3.0f))
                .saturationRegen(false)
                .build();
    }
}
