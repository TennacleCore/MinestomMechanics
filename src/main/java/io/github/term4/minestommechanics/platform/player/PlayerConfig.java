package io.github.term4.minestommechanics.platform.player;

import org.jetbrains.annotations.Nullable;

/**
 * Immutable player platform config (per-player server behavior, not combat mechanics). Scoped like the
 * combat configs via {@code MechanicsProfile.player} and applied at spawn (join / instance change) by
 * {@link PlayerConfigApplier}. Plain values only - these are rarely-changing platform knobs, not
 * per-hit values, so there is deliberately no {@code FieldValue}/subconfig machinery here. Unset
 * ({@code null}) fields are left unmanaged.
 */
public final class PlayerConfig {

    /** Position broadcast interval in ticks (1 = every tick, the Minestom default). */
    public final @Nullable Integer positionBroadcastInterval;

    private PlayerConfig(Builder b) {
        positionBroadcastInterval = b.positionBroadcastInterval;
    }

    public Builder toBuilder() { return new Builder(this); }

    public static Builder builder() { return builder(null); }
    public static Builder builder(@Nullable PlayerConfig base) { return base != null ? new Builder(base) : new Builder(); }

    public static final class Builder {
        private @Nullable Integer positionBroadcastInterval;

        Builder() {}

        Builder(PlayerConfig c) {
            positionBroadcastInterval = c.positionBroadcastInterval;
        }

        public Builder positionBroadcastInterval(@Nullable Integer v) { positionBroadcastInterval = v; return this; }

        public PlayerConfig build() { return new PlayerConfig(this); }
    }
}
