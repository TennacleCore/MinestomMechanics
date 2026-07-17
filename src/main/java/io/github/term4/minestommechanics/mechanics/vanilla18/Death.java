package io.github.term4.minestommechanics.mechanics.vanilla18;

import io.github.term4.minestommechanics.mechanics.death.DeathConfig;

/**
 * Vanilla 1.8 death/respawn cleanup defaults: active effects cleared, transient combat state reset, and the dead body
 * hidden from viewers past the 20-tick death animation. Consumed by the {@link Vanilla18} preset profile.
 */
public final class Death {

    private Death() {}

    public static DeathConfig config() {
        return DeathConfig.builder()
                .clearEffects(true)
                .resetCombatState(true)
                .hideCorpse(true)
                .deathAnimationTicks(20)
                .build();
    }
}
