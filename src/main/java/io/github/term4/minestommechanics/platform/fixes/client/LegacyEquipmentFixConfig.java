package io.github.term4.minestommechanics.platform.fixes.client;

import io.github.term4.minestommechanics.platform.fixes.FixToggle;
import org.jetbrains.annotations.Nullable;

/**
 * Strips empty (AIR) slots from outgoing {@code EntityEquipmentPacket}s, matching vanilla ({@link LegacyEquipmentFix}).
 * Rides the OptimizedPlayer send-packet override: install-level toggle, not per-scope; applies to every client.
 */
public final class LegacyEquipmentFixConfig implements FixToggle {

    private final @Nullable Boolean enabled;

    private LegacyEquipmentFixConfig(Builder b) { this.enabled = b.enabled; }

    public @Nullable Boolean enabled() { return enabled; }

    /** Merges this config over {@code base}: this config's set knob wins, unset falls back to {@code base}. */
    public LegacyEquipmentFixConfig fromBase(LegacyEquipmentFixConfig base) {
        return new Builder().enabled(enabled != null ? enabled : base.enabled).build();
    }

    public Builder toBuilder() { return new Builder(this); }
    public static Builder builder() { return new Builder(); }
    public static Builder builder(@Nullable LegacyEquipmentFixConfig base) { return base != null ? new Builder(base) : new Builder(); }

    public static final class Builder {
        private @Nullable Boolean enabled;

        Builder() {}
        Builder(LegacyEquipmentFixConfig c) { enabled = c.enabled; }

        public Builder enabled(@Nullable Boolean v) { this.enabled = v; return this; }

        public LegacyEquipmentFixConfig build() { return new LegacyEquipmentFixConfig(this); }
    }
}
