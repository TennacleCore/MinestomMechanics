package io.github.term4.minestommechanics.platform.fixes.client;

import org.jetbrains.annotations.Nullable;

/**
 * Config for the view-distance clamp fix - the {@code legacyViewDistanceFix} member of
 * {@link io.github.term4.minestommechanics.platform.fixes.FixesConfig}. When enabled, a client's reported view distance
 * is clamped to the instance's before Minestom processes it, so {@code refreshSettings} does not over-send chunks (the
 * legacy-client "invisibility band" far from spawn). See {@link LegacyViewDistanceFix}.
 *
 * <p>It rides the {@link io.github.term4.minestommechanics.platform.player.OptimizedPlayer} refresh-settings override (a
 * server-wide transform), so this is an install-level toggle ({@code null} = off), not a per-scope one. No-op for any
 * client already within the instance's view distance. Temporary workaround; removable once the upstream Minestom fix
 * (branch {@code fix/refresh-settings-view-distance}) is on the pinned dependency.
 */
public final class LegacyViewDistanceFixConfig {

    private final @Nullable Boolean enabled;

    private LegacyViewDistanceFixConfig(Builder b) { this.enabled = b.enabled; }

    /** Whether a client's view distance is clamped to the instance's before {@code refreshSettings} runs; {@code null} = off. */
    public @Nullable Boolean enabled() { return enabled; }

    /** Merges this config over {@code base}: this config's set knob wins, unset falls back to {@code base}. */
    public LegacyViewDistanceFixConfig fromBase(LegacyViewDistanceFixConfig base) {
        return new Builder().enabled(enabled != null ? enabled : base.enabled).build();
    }

    public Builder toBuilder() { return new Builder(this); }
    public static Builder builder() { return new Builder(); }
    public static Builder builder(@Nullable LegacyViewDistanceFixConfig base) { return base != null ? new Builder(base) : new Builder(); }

    public static final class Builder {
        private @Nullable Boolean enabled;

        Builder() {}
        Builder(LegacyViewDistanceFixConfig c) { enabled = c.enabled; }

        public Builder enabled(@Nullable Boolean v) { this.enabled = v; return this; }

        public LegacyViewDistanceFixConfig build() { return new LegacyViewDistanceFixConfig(this); }
    }
}
