package io.github.term4.minestommechanics.platform.fixes.client;

import io.github.term4.minestommechanics.platform.fixes.FixToggle;
import org.jetbrains.annotations.Nullable;

/**
 * The legacy 1.8/Via consume fix (eating under lag). Unlike a 1.13.2+ client, a 1.8 client neither gates its own
 * consumption nor learns the eaten count from the server: a laggy one spam-restarts a use (double-eats) and races the
 * held-stack count. When on, {@code ConsumableSystem} refuses a re-use while already mid-use, decrements the held slot
 * silently, and paces the count with an {@code entity_status 9} + {@code window_items} confirm each finish. Legacy
 * clients only - a modern client handles its own consumption, so it never touches one.
 */
public final class LegacyConsumeFixConfig implements FixToggle {

    private final @Nullable Boolean enabled;

    private LegacyConsumeFixConfig(Builder b) { this.enabled = b.enabled; }

    /** Whether the legacy consume fix is applied to legacy clients; {@code null} = off. */
    public @Nullable Boolean enabled() { return enabled; }

    /** Merges this config over {@code base}: this config's set knob wins, unset falls back to {@code base}. */
    public LegacyConsumeFixConfig fromBase(LegacyConsumeFixConfig base) {
        return new Builder().enabled(enabled != null ? enabled : base.enabled).build();
    }

    public Builder toBuilder() { return new Builder(this); }
    public static Builder builder() { return new Builder(); }
    public static Builder builder(@Nullable LegacyConsumeFixConfig base) { return base != null ? new Builder(base) : new Builder(); }

    public static final class Builder {
        private @Nullable Boolean enabled;

        Builder() {}
        Builder(LegacyConsumeFixConfig c) { enabled = c.enabled; }

        public Builder enabled(@Nullable Boolean v) { this.enabled = v; return this; }

        public LegacyConsumeFixConfig build() { return new LegacyConsumeFixConfig(this); }
    }
}
