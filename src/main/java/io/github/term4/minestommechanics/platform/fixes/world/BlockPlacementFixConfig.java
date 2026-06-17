package io.github.term4.minestommechanics.platform.fixes.world;

import org.jetbrains.annotations.Nullable;

/**
 * Config for the block-placement sync fix - the {@code blockPlacement} member of
 * {@link io.github.term4.minestommechanics.platform.fixes.FixesConfig}. A temporary local override of Minestom's
 * {@code BlockPlacementListener}: when a placement is cancelled (collides with an entity, read-only chunk, cancelled
 * event, or denied), stock Minestom resends the whole chunk to the player; a chunk resend drops that chunk's entities
 * from an older client's per-chunk index, so a player who places a block onto another player can no longer interact
 * with them. This fix sends a targeted block change instead. Driven by {@link BlockPlacementFix}.
 *
 * <p>The override replaces a server-wide packet listener, so this is an install-level toggle ({@code null} = off), not
 * a per-scope one. Remove once the upstream Minestom fix is on the pinned dependency.
 */
public final class BlockPlacementFixConfig {

    private final @Nullable Boolean enabled;

    private BlockPlacementFixConfig(Builder b) { this.enabled = b.enabled; }

    /** Whether the targeted-block-change placement fix is installed; {@code null} = off. */
    public @Nullable Boolean enabled() { return enabled; }

    /** Merges this config over {@code base}: this config's set knob wins, unset falls back to {@code base}. */
    public BlockPlacementFixConfig fromBase(BlockPlacementFixConfig base) {
        return new Builder().enabled(enabled != null ? enabled : base.enabled).build();
    }

    public Builder toBuilder() { return new Builder(this); }
    public static Builder builder() { return new Builder(); }
    public static Builder builder(@Nullable BlockPlacementFixConfig base) { return base != null ? new Builder(base) : new Builder(); }

    public static final class Builder {
        private @Nullable Boolean enabled;

        Builder() {}
        Builder(BlockPlacementFixConfig c) { enabled = c.enabled; }

        public Builder enabled(@Nullable Boolean v) { this.enabled = v; return this; }

        public BlockPlacementFixConfig build() { return new BlockPlacementFixConfig(this); }
    }
}
