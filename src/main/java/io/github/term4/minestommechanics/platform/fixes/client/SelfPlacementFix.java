package io.github.term4.minestommechanics.platform.fixes.client;

import io.github.term4.minestommechanics.platform.fixes.world.BlockPlacementFix;
import io.github.term4.minestommechanics.platform.player.OptimizedPlayer;
import io.github.term4.minestommechanics.util.BlockContact;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.item.Material;
import net.minestom.server.network.packet.client.play.ClientPlayerBlockPlacementPacket;

/**
 * 1.8 self-placement: vanilla 1.8 excludes the placer from the placement collision check (place a ladder into your own
 * body); Minestom doesn't, so the block desyncs. Arms {@link OptimizedPlayer#setSelfPlacing} per-placement - only the
 * placer, only during their own placement, and only for PASSABLE blocks (a motion-blocking block into your own hitbox
 * is never a legit clutch - stricter than 1.8, an anti-cheat guard). Wraps {@link BlockPlacementFix#listener}; once
 * that chunk fix is upstream, point {@link #wrapped} at the stock listener - this class stays.
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
