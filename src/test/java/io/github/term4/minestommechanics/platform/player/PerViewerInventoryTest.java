package io.github.term4.minestommechanics.platform.player;

import io.github.term4.minestommechanics.platform.compatibility.Compat18;
import io.github.term4.minestommechanics.testsupport.HeadlessServerTest;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.network.packet.server.SendablePacket;
import net.minestom.server.network.packet.server.play.SetPlayerInventorySlotPacket;
import net.minestom.server.network.packet.server.play.SetSlotPacket;
import net.minestom.server.network.player.GameProfile;
import net.minestom.server.network.player.PlayerConnection;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * The per-viewer inventory keeps an incremental slot refresh transformable: it reaches the client bare (not a shared
 * {@code CachedPacket}), so the outgoing reskin/stamp lands on it - the fix for the swing/attack-range reverting after
 * the first throw or single-item drop.
 */
class PerViewerInventoryTest extends HeadlessServerTest {

    static final class CapturingConnection extends PlayerConnection {
        final List<SendablePacket> sent = new CopyOnWriteArrayList<>();

        @Override
        public void sendPacket(SendablePacket packet) { sent.add(packet); }

        @Override
        public SocketAddress getRemoteAddress() { return new InetSocketAddress("localhost", 25565); }

        @Override
        public void disconnect() {}
    }

    @Test
    void incrementalSlotRefreshReachesClientReskinned() {
        var conn = new CapturingConnection();
        OptimizedPlayer p = new OptimizedPlayer(conn, new GameProfile(UUID.randomUUID(), "InvTest"));
        p.compat().apply(Compat18.config()); // suppressThrowSwing on
        assertInstanceOf(PerViewerInventory.class, p.getInventory());
        p.getInventory().addViewer(p); // a real join adds the owner at spawn

        conn.sent.clear();
        p.getInventory().setItemStack(0, ItemStack.of(Material.SNOWBALL)); // the throw/drop path: a single-slot refresh

        SendablePacket slot = conn.sent.stream()
                .filter(x -> x instanceof SetPlayerInventorySlotPacket || x instanceof SetSlotPacket)
                .findFirst().orElseThrow(() -> new AssertionError("no slot refresh reached the client"));
        ItemStack shown = slot instanceof SetPlayerInventorySlotPacket sp ? sp.itemStack() : ((SetSlotPacket) slot).itemStack();
        assertEquals(Material.PAPER, shown.material(), "the incremental slot refresh is reskinned, not reverted to a raw snowball");
    }
}
