package test.presets.mmc18;

import io.github.term4.minestommechanics.platform.player.PlayerConfig;

/** mmc18 player platform config: the vanilla 1.8 base with a 1-tick position broadcast. */
public final class Player {

    private Player() {}

    /** PlayerConfig: the vanilla 1.8 base with a 1-tick position broadcast. */
    public static PlayerConfig config() {
        return PlayerConfig.builder(io.github.term4.minestommechanics.mechanics.vanilla18.Player.config())
                .positionBroadcastInterval(1)
                .build();
    }
}
