package io.github.term4.minestommechanics.platform;

import net.minestom.server.network.packet.server.SendablePacket;
import net.minestom.server.network.packet.server.ServerPacket;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Packet unwrapping for every-outgoing-packet rewrite paths (equipment strip, attack-range stamp, throwable reskin).
 *
 * <p>Resolves only the state-independent shape: a bare {@link ServerPacket}. NEVER route these
 * paths through {@code extractServerPacket}: its {@code CachedPacket} branch forces that packet's single, stateless
 * {@code FramedPacket} cache for whatever connection state is passed - a hardcoded PLAY poisons the cache during a
 * modern client's CONFIGURATION join and corrupts the real config send. The rewritten packets reach the player bare
 * ({@code PerViewerInventory} delivers slot refreshes uncached), so skipping the CachedPacket shape loses no coverage.
 */
public final class PacketShapes {

    private PacketShapes() {}

    /** The unwrapped {@link ServerPacket}, or {@code null} for shapes that can't be resolved statelessly. */
    public static @Nullable ServerPacket unwrapStateless(@NotNull SendablePacket packet) {
        return packet instanceof ServerPacket sp ? sp : null;
    }
}
