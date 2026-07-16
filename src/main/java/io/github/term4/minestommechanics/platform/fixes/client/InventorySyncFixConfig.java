package io.github.term4.minestommechanics.platform.fixes.client;

import io.github.term4.minestommechanics.platform.fixes.FixToggle;
import org.jetbrains.annotations.Nullable;

/**
 * Enables {@link InventorySync}: the vanilla remote-slot echo suppression Minestom omits. When on, a slot update the
 * client already predicts is dropped instead of re-sent, so a laggy move-and-revert no longer flickers (and no stray
 * held-slot echo interrupts an eat). Server-wide, so it reads the install config, not the per-scope profile.
 *
 * <p><b>Experimental.</b> Re-implements Minestom's whole player-inventory click model (a mirror + a line-for-line port
 * of every click type) to know what the client already shows; a Minestom click-logic change can drift the port (worst
 * case a slot flickers, never a stuck desync). Opt-in, and verify per deployment.
 */
public final class InventorySyncFixConfig implements FixToggle {

    private final @Nullable Boolean enabled;

    private InventorySyncFixConfig(Builder b) { this.enabled = b.enabled; }

    /** Whether inventory slot echoes are suppressed against the client's predicted state; {@code null} = off. */
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
