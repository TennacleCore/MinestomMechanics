package io.github.term4.minestommechanics.platform.fixes.client;

import io.github.term4.minestommechanics.platform.fixes.FixToggle;
import org.jetbrains.annotations.Nullable;

/**
 * Enables {@link InventorySync}: the vanilla remote-slot echo suppression Minestom omits - a slot update the client
 * already predicts is dropped instead of re-sent. Server-wide, so it reads the install config, not the per-scope
 * profile.
 *
 * <p><b>Experimental</b>: a click-logic change upstream can drift the ported click model (worst case a slot flickers,
 * never a stuck desync). Verify per deployment.
 */
public final class InventorySyncFixConfig implements FixToggle {

    private final @Nullable Boolean enabled;

    private InventorySyncFixConfig(Builder b) { this.enabled = b.enabled; }

    public @Nullable Boolean enabled() { return enabled; }

    /** Merges this config over {@code base}: this config's set knob wins, unset falls back to {@code base}. */
    public InventorySyncFixConfig fromBase(InventorySyncFixConfig base) {
        return new Builder().enabled(enabled != null ? enabled : base.enabled).build();
    }

    public Builder toBuilder() { return new Builder(this); }
    public static Builder builder() { return new Builder(); }
    public static Builder builder(@Nullable InventorySyncFixConfig base) { return base != null ? new Builder(base) : new Builder(); }

    public static final class Builder {
        private @Nullable Boolean enabled;

        Builder() {}
        Builder(InventorySyncFixConfig c) { enabled = c.enabled; }

        public Builder enabled(@Nullable Boolean v) { this.enabled = v; return this; }

        public InventorySyncFixConfig build() { return new InventorySyncFixConfig(this); }
    }
}
