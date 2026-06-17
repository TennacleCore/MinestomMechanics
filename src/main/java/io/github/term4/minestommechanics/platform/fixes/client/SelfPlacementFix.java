package io.github.term4.minestommechanics.platform.fixes.client;

import io.github.term4.minestommechanics.platform.fixes.world.BlockPlacementFix;
import io.github.term4.minestommechanics.platform.player.OptimizedPlayer;
import io.github.term4.minestommechanics.util.BlockContact;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.item.Material;
import net.minestom.server.network.packet.client.play.ClientPlayerBlockPlacementPacket;

/**
 * The 1.8 self-placement compat fix's install hook (mirrors {@link MetaFix}): wraps the block-placement listener so a
 * player is excluded from the placement collision check while processing their own placement. Vanilla 1.8 excludes the
 * placing entity, so a 1.8 client can place a block into its own body (e.g. extend the ladder it is climbing); modern
 * vanilla - and stock Minestom's {@code canPlaceBlockAt} - does not, so the placement is rejected server-side though it
 * placed locally, and the block desyncs. The exclusion is delivered via {@link OptimizedPlayer#preventBlockPlacement()}
 * ({@code canPlaceBlockAt} skips an entity returning false), armed here per-placement - only the placer, only during
 * their own placement. Config: {@link SelfPlacementFixConfig}.
 *
 * <p>Only passable blocks are allowed into the body - ladders, vines, cobwebs, plants. Any block that blocks movement
 * (stairs, slabs, fences, full cubes) is refused: placing one into your own hitbox is self-suffocating and never a
 * legit clutch (so this is stricter than vanilla 1.8, which excludes the placer for everything - an anti-cheat guard).
 * Passability is {@link BlockContact#isPassable} (Minestom has no {@code blocksMotion} flag, so it's approximated); may
 * be slightly off for an edge-case block, so revisit if one is wrongly allowed or refused.
 *
 * <p>It wraps {@link BlockPlacementFix#listener} (the local chunk-resend correction), so enabling it also installs that
 * fix. Once the chunk fix is upstream, delete {@code BlockPlacementFix} and point {@link #wrapped} at the upstream
 * {@code BlockPlacementListener.listener}; this class and the OptimizedPlayer override stay.
 */
public final class SelfPlacementFix {

    private SelfPlacementFix() {}

    /** Installs the block-placement listener wrapped with the 1.8 self-placement exclusion (delegates to the corrected {@link BlockPlacementFix#listener}). */
    public static void install() {
        MinecraftServer.getPacketListenerManager().setPlayListener(ClientPlayerBlockPlacementPacket.class, SelfPlacementFix::wrapped);
    }

    /** Arms {@link OptimizedPlayer#setSelfPlacing} for a passable block placement, then delegates (try/finally, like {@code MetaFix.wrapListener}). */
    private static void wrapped(ClientPlayerBlockPlacementPacket packet, Player player) {
        OptimizedPlayer op = player instanceof OptimizedPlayer o
                && excludesPlacer(player.getItemInHand(packet.hand()).material()) ? o : null;
        if (op != null) op.setSelfPlacing(true);
        try {
            BlockPlacementFix.listener(packet, player);
        } finally {
            if (op != null) op.setSelfPlacing(false);
        }
    }

    /**
     * Whether placing {@code m} should exclude the placer: a placeable, {@link BlockContact#isPassable passable} block
     * (ladders, vines, cobwebs, ...). The {@code isBlock()} guard is required - a non-block item (sword/bucket/food
     * right-clicking a block) has a {@code null} {@link Material#block()}; a movement-blocking or non-block material is
     * left to the stock self-collision check anyway.
     */
    private static boolean excludesPlacer(Material m) {
        return m.isBlock() && BlockContact.isPassable(m.block());
    }
}
