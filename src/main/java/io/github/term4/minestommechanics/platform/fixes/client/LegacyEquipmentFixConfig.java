package io.github.term4.minestommechanics.platform.fixes.client;

import io.github.term4.minestommechanics.platform.fixes.FixToggle;
import org.jetbrains.annotations.Nullable;

/**
 * Config for the legacy equipment-slot fix - the {@code legacyEquipmentFix} member of
 * {@link io.github.term4.minestommechanics.platform.fixes.FixesConfig}. When enabled, empty (AIR) equipment slots are stripped
 * from outgoing {@code EntityEquipmentPacket}s to legacy clients, matching what vanilla emits. See {@link LegacyEquipmentFix}.
 *
 * <p>It rides the {@link io.github.term4.minestommechanics.platform.player.OptimizedPlayer} send-packet override (a server-wide
 * transform), so this is an install-level toggle ({@code null} = off), not a per-scope one. Applied to all clients - harmless for
 * modern ones, since dropping empty slots is vanilla parity. Temporary workaround; removable once the upstream
 * {@code getEquipmentsPacket} fix (branch {@code fix/skip-empty-equipment-slots}) is on the pinned dependency.
 */
public final class LegacyEquipmentFixConfig implements FixToggle {

    private final @Nullable Boolean enabled;

    private LegacyEquipmentFixConfig(Builder b) { this.enabled = b.enabled; }

    /** Whether empty equipment slots are stripped from outgoing equipment packets to legacy clients; {@code null} = off. */
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
