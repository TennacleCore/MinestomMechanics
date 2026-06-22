package io.github.term4.minestommechanics.mechanics.durability;

import org.jetbrains.annotations.Nullable;

/**
 * Config for item durability (damage-on-use). Assigned per scope via the
 * {@link io.github.term4.minestommechanics.MechanicsProfile} {@code durability} member, resolved player -&gt; instance
 * -&gt; global with the install config as the final fallback. Installed by {@link DurabilitySystem}.
 *
 * <p><b>Stub.</b> Only {@link #enabled} exists today; the per-use damage amounts (attack / mine / block-hit / armor-hit),
 * Unbreaking handling, and on-break behavior land with the durability logic.
 */
public final class DurabilityConfig {

    private final @Nullable Boolean enabled;

    private DurabilityConfig(Builder b) { this.enabled = b.enabled; }

    /** Whether durability damage is applied (unset = active; set {@code false} to disable). */
    public @Nullable Boolean enabled() { return enabled; }

    /** Merges this config over {@code base} (this if set, else base). */
    public DurabilityConfig fromBase(DurabilityConfig base) {
        return new Builder().enabled(enabled != null ? enabled : base.enabled).build();
    }

    public Builder toBuilder() { return new Builder(this); }
    public static Builder builder() { return new Builder(); }
    public static Builder builder(@Nullable DurabilityConfig base) { return base != null ? new Builder(base) : new Builder(); }

    public static final class Builder {
        private @Nullable Boolean enabled;

        Builder() {}
        Builder(DurabilityConfig c) { enabled = c.enabled; }

        public Builder enabled(@Nullable Boolean v) { this.enabled = v; return this; }

        public DurabilityConfig build() { return new DurabilityConfig(this); }
    }
}
