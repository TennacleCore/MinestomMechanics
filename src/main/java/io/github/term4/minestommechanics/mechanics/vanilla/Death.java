package io.github.term4.minestommechanics.mechanics.vanilla;

import io.github.term4.minestommechanics.mechanics.death.DeathConfig;

/**
 * Modern (26.1+) death/respawn cleanup defaults. Death cleanup is version-neutral, so these match the 1.8 values
 * (effects cleared, combat state reset, body hidden past the 20-tick death animation). Consumed by the {@link Vanilla} preset profile.
 */
public final class Death {

    private Death() {}

    /** DeathConfig with modern (26.1) defaults. */
    public static DeathConfig config() {
        return DeathConfig.builder()
                .clearEffects(true)
                .resetCombatState(true)
                .hideCorpse(true)
                .deathAnimationTicks(20)
                .build();
    }
}
