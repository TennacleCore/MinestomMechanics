package io.github.term4.minestommechanics.platform.fixes.visuals.legacy_1_8;

import org.jetbrains.annotations.Nullable;

/**
 * Config for the 1.8-client "arrow goes invisible after touching an entity" fix - one fix, two knobs. {@link #enabled}
 * is the team trick (co-team the enabled players on a neutral friendly-fire-off scoreboard team so a 1.8 client's own
 * arrow-collision stops hiding deflected / passed-through arrows) - the actual visibility fix, purely visual since
 * server-side damage ignores teams, driven by {@link LegacyArrowVisibility}. {@link #deflectParticles} is an optional
 * cosmetic crit-particle trail along a deflected / passed arrow's path (a Hypixel-style flourish), orthogonal to
 * {@code enabled}. Both resolve per player through the {@code MechanicsProfiles} chain (the fix is a persistent
 * scoreboard team, so it cannot vary per in-flight arrow).
 */
public final class LegacyArrowVisibilityConfig {

    private final @Nullable Boolean enabled;
    private final @Nullable Boolean deflectParticles;

    private LegacyArrowVisibilityConfig(Builder b) {
        this.enabled = b.enabled;
        this.deflectParticles = b.deflectParticles;
    }

    /** Whether the team-trick visibility fix is on (keeps deflected / passed arrows visible on 1.8); {@code null} = off. */
    public @Nullable Boolean enabled() { return enabled; }
    /** Whether the optional cosmetic crit-particle deflect trail is on; {@code null} = off. */
    public @Nullable Boolean deflectParticles() { return deflectParticles; }

    /** Merges this config over {@code base}: this config's set knobs win, unset fall back to {@code base}. */
    public LegacyArrowVisibilityConfig fromBase(LegacyArrowVisibilityConfig base) {
        return new Builder()
                .enabled(enabled != null ? enabled : base.enabled)
                .deflectParticles(deflectParticles != null ? deflectParticles : base.deflectParticles)
                .build();
    }

    public Builder toBuilder() { return new Builder(this); }
    public static Builder builder() { return new Builder(); }
    public static Builder builder(@Nullable LegacyArrowVisibilityConfig base) { return base != null ? new Builder(base) : new Builder(); }

    public static final class Builder {
        private @Nullable Boolean enabled;
        private @Nullable Boolean deflectParticles;

        Builder() {}
        Builder(LegacyArrowVisibilityConfig c) { enabled = c.enabled; deflectParticles = c.deflectParticles; }

        /** The team-trick visibility fix (keeps deflected / passed arrows visible on 1.8 clients). */
        public Builder enabled(@Nullable Boolean v) { this.enabled = v; return this; }
        /** Optional cosmetic crit-particle trail along the deflected / passed arrow (Hypixel-style; off by default). */
        public Builder deflectParticles(@Nullable Boolean v) { this.deflectParticles = v; return this; }

        public LegacyArrowVisibilityConfig build() { return new LegacyArrowVisibilityConfig(this); }
    }
}
