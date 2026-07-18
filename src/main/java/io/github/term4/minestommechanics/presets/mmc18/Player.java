package io.github.term4.minestommechanics.presets.mmc18;

import io.github.term4.minestommechanics.platform.player.PlayerConfig;
import io.github.term4.minestommechanics.presets.vanilla18.Vanilla18;

/** mmc18 player platform config: the vanilla 1.8 base with a 1-tick position broadcast. */
public final class Player {

    private Player() {}

    /** PlayerConfig: the vanilla 1.8 base with a 1-tick position broadcast. */
    public static PlayerConfig config() {
        return PlayerConfig.builder(Vanilla18.player())
                .positionBroadcastInterval(1)
                .build();
    }
}
