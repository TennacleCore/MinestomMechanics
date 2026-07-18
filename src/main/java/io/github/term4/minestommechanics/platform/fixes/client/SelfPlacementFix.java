package io.github.term4.minestommechanics.platform.fixes.client;

import io.github.term4.minestommechanics.platform.player.OptimizedPlayer;
import io.github.term4.minestommechanics.util.BlockContact;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.item.Material;
import net.minestom.server.listener.BlockPlacementListener;
import net.minestom.server.network.packet.client.play.ClientPlayerBlockPlacementPacket;
import org.jetbrains.annotations.NotNull;

import java.util.function.BiConsumer;

/**
 * 1.8 self-placement: vanilla 1.8 excludes the placer from the placement collision check (place a ladder into your own
 * body); Minestom doesn't, so the block desyncs. Arms {@link OptimizedPlayer#setSelfPlacing} per-placement - only the
 * placer, only during their own placement, and only for PASSABLE blocks (a motion-blocking block into your own hitbox
 * is never a legit clutch - stricter than 1.8, an anti-cheat guard). Wraps the stock listener by default; an app that
 * replaces the placement listener (e.g. a shard library's world-scoped one) re-installs with it as the delegate, LAST.
 */
public final class SelfPlacementFix {

    private SelfPlacementFix() {}

    /** Installs the stock placement listener wrapped with the 1.8 self-placement exclusion. */
    public static void install() {
        install(BlockPlacementListener::listener);
    }

    /** Installs {@code delegate} wrapped with the exclusion - the composition seam for replaced placement listeners. */
    public static void install(@NotNull BiConsumer<ClientPlayerBlockPlacementPacket, Player> delegate) {
        MinecraftServer.getPacketListenerManager().setPlayListener(ClientPlayerBlockPlacementPacket.class,
                wrap(delegate)::accept);
    }

    /** {@code delegate} wrapped with the exclusion, uninstalled - for hosts that own the listener slot (a shard
     *  library's placement-decorator seam), where registration order must not matter. */
    public static @NotNull BiConsumer<ClientPlayerBlockPlacementPacket, Player> wrap(
            @NotNull BiConsumer<ClientPlayerBlockPlacementPacket, Player> delegate) {
        return (packet, player) -> wrapped(delegate, packet, player);
    }

    /** Arms {@link OptimizedPlayer#setSelfPlacing} for a passable block placement, then delegates (try/finally, like {@code MetaFix.wrapListener}). */
    private static void wrapped(BiConsumer<ClientPlayerBlockPlacementPacket, Player> delegate,
                                ClientPlayerBlockPlacementPacket packet, Player player) {
        OptimizedPlayer op = player instanceof OptimizedPlayer o
                && excludesPlacer(player.getItemInHand(packet.hand()).material()) ? o : null;
        if (op != null) op.setSelfPlacing(true);
        try {
            delegate.accept(packet, player);
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
