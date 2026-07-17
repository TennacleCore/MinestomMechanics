package io.github.term4.minestommechanics.mechanics.vanilla18;

import io.github.term4.minestommechanics.mechanics.blocking.BlockingBehavior;
import io.github.term4.minestommechanics.mechanics.blocking.BlockingConfig;
import io.github.term4.minestommechanics.mechanics.blocking.BlockingTypeConfig;
import io.github.term4.minestommechanics.mechanics.blocking.catalog.VanillaBlocking;

/**
 * Vanilla 1.8 blocking: sword block on the {@link VanillaBlocking#SWORDS sword materials}. Reduces a blocked hit to
 * {@code (1 + f) * 0.5} (vanilla {@code EntityHuman.damageEntity}, pre-armor) via the {@code SWORD} behavior ({@code base
 * -0.5, factor 0.5}); omnidirectional, no server-side movement slowdown (client-predicted), and only non-armor-bypassing
 * damage. A MODERN preset maps the shield to {@code SHIELD}.
 */
public final class Blocking {

    private Blocking() {}

    public static BlockingConfig config() {
        return BlockingConfig.builder()
                .defaults(BlockingTypeConfig.builder()
                        .behavior(BlockingBehavior.SWORD)
                        .reductionBase(-0.5).reductionFactor(0.5)
                        .build())
                .materials(VanillaBlocking.SWORDS)
                .build();
    }
}
