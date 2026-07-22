package io.github.term4.minestommechanics.presets.vanilla;

import io.github.term4.minestommechanics.mechanics.death.DeathConfig;

/** Modern (26.1+) death/respawn cleanup defaults; death cleanup is version-neutral, so these match the 1.8 values. */
public final class Death {

    private Death() {}

    public static DeathConfig config() {
        return DeathConfig.builder()
                .clearEffects(true)
                .resetMechanicsState(true)
                .hideCorpse(true)
                .deathAnimationTicks(20)
                .build();
    }
}
