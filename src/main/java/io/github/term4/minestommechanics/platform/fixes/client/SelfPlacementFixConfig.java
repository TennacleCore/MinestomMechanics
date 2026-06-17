package io.github.term4.minestommechanics.platform.fixes.client;

import org.jetbrains.annotations.Nullable;

/**
 * Config for the 1.8 self-placement compat fix - the {@code selfPlacement} member of
 * {@link io.github.term4.minestommechanics.platform.fixes.FixesConfig}. When enabled, a placing player is excluded
 * from the placement collision check for the duration of their own placement, so a 1.8 client can place a block into
 * its own body (e.g. extend the ladder it is climbing) the way vanilla 1.8 allows. Only passable blocks are allowed
 * in - ladders, vines, cobwebs, plants; any movement-blocking block (stairs/slabs/fences/full) is refused
 * (self-suffocating, an anti-cheat guard). No-op for modern clients, so it is safe to leave on. See
 * {@link SelfPlacementFix} for the mechanism.
 *
 * <p>It rides the same server-wide packet listener as the {@code blockPlacement} sync fix, so this is an install-level
 * toggle ({@code null} = off), not a per-scope one - and enabling it installs that listener (the chunk-resend fix) too.
 */
public final class SelfPlacementFixConfig {

    private final @Nullable Boolean enabled;

    private SelfPlacementFixConfig(Builder b) { this.enabled = b.enabled; }

    /** Whether a player may place a block into its own body (the 1.8 exclude-placer rule); {@code null} = off. */
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
