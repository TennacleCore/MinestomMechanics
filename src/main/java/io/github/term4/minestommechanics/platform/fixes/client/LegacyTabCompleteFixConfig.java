package io.github.term4.minestommechanics.platform.fixes.client;

import io.github.term4.minestommechanics.platform.fixes.FixToggle;
import org.jetbrains.annotations.Nullable;

/**
 * Answers command-name tab-completion for legacy clients - Minestom only completes arguments
 * ({@link LegacyTabCompleteFix}). Replaces the packet listener server-wide: install-level toggle, not per-scope.
 */
public final class LegacyTabCompleteFixConfig implements FixToggle {

    private final @Nullable Boolean enabled;

    private LegacyTabCompleteFixConfig(Builder b) { this.enabled = b.enabled; }

    public @Nullable Boolean enabled() { return enabled; }

    /** Merges this config over {@code base}: this config's set knob wins, unset falls back to {@code base}. */
    public LegacyTabCompleteFixConfig fromBase(LegacyTabCompleteFixConfig base) {
        return new Builder().enabled(enabled != null ? enabled : base.enabled).build();
    }

    public Builder toBuilder() { return new Builder(this); }
    public static Builder builder() { return new Builder(); }
    public static Builder builder(@Nullable LegacyTabCompleteFixConfig base) { return base != null ? new Builder(base) : new Builder(); }

    public static final class Builder {
        private @Nullable Boolean enabled;

        Builder() {}
        Builder(LegacyTabCompleteFixConfig c) { enabled = c.enabled; }

        public Builder enabled(@Nullable Boolean v) { this.enabled = v; return this; }

        public LegacyTabCompleteFixConfig build() { return new LegacyTabCompleteFixConfig(this); }
    }
}
