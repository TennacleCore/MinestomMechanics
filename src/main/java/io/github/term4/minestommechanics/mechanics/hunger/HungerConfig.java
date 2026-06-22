package io.github.term4.minestommechanics.mechanics.hunger;

import org.jetbrains.annotations.Nullable;

/**
 * Config for the hunger subsystem (food / saturation / exhaustion, natural regen, starvation). Assigned per scope via
 * the {@link io.github.term4.minestommechanics.MechanicsProfile} {@code hunger} member, resolved player -&gt; instance
 * -&gt; global with the install config as the final fallback. Installed by {@link HungerSystem}.
 *
 * <p><b>Stub.</b> Only {@link #enabled} exists today; exhaustion costs, the saturation/regen cadence, and starvation
 * damage land with the hunger logic (and feed the consumables eat-&gt;effect flow).
 */
public final class HungerConfig {

    private final @Nullable Boolean enabled;

    private HungerConfig(Builder b) { this.enabled = b.enabled; }

    /** Whether hunger is simulated (unset = active; set {@code false} to disable). */
    public @Nullable Boolean enabled() { return enabled; }

    /** Merges this config over {@code base} (this if set, else base). */
    public HungerConfig fromBase(HungerConfig base) {
        return new Builder().enabled(enabled != null ? enabled : base.enabled).build();
    }

    public Builder toBuilder() { return new Builder(this); }
    public static Builder builder() { return new Builder(); }
    public static Builder builder(@Nullable HungerConfig base) { return base != null ? new Builder(base) : new Builder(); }

    public static final class Builder {
        private @Nullable Boolean enabled;

        Builder() {}
        Builder(HungerConfig c) { enabled = c.enabled; }

        public Builder enabled(@Nullable Boolean v) { this.enabled = v; return this; }

        public HungerConfig build() { return new HungerConfig(this); }
    }
}
