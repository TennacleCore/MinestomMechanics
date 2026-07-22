package io.github.term4.minestommechanics.platform.fixes.client;

import io.github.term4.minestommechanics.platform.fixes.FixToggle;
import org.jetbrains.annotations.Nullable;

/**
 * Lets a placer put PASSABLE blocks into their own body (ladder/vine/web, vanilla 1.8 behavior); movement-blocking
 * blocks stay refused ({@link SelfPlacementFix}). Wraps the server-wide placement listener, so it's an
 * install-level toggle, not per-scope. No-op for modern clients.
 */
public final class SelfPlacementFixConfig implements FixToggle {

    private final @Nullable Boolean enabled;

    private SelfPlacementFixConfig(Builder b) { this.enabled = b.enabled; }

    public @Nullable Boolean enabled() { return enabled; }

    /** Merges this config over {@code base}: this config's set knob wins, unset falls back to {@code base}. */
    public SelfPlacementFixConfig fromBase(SelfPlacementFixConfig base) {
        return new Builder().enabled(enabled != null ? enabled : base.enabled).build();
    }

    public Builder toBuilder() { return new Builder(this); }
    public static Builder builder() { return new Builder(); }
    public static Builder builder(@Nullable SelfPlacementFixConfig base) { return base != null ? new Builder(base) : new Builder(); }

    public static final class Builder {
        private @Nullable Boolean enabled;

        Builder() {}
        Builder(SelfPlacementFixConfig c) { enabled = c.enabled; }

        public Builder enabled(@Nullable Boolean v) { this.enabled = v; return this; }

        public SelfPlacementFixConfig build() { return new SelfPlacementFixConfig(this); }
    }
}
