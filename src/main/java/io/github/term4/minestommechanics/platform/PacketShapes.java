package io.github.term4.minestommechanics.platform;

import net.minestom.server.network.packet.server.LazyPacket;
import net.minestom.server.network.packet.server.SendablePacket;
import net.minestom.server.network.packet.server.ServerPacket;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Packet unwrapping for every-outgoing-packet rewrite paths (equipment strip, attack-range stamp).
 *
 * <p>Resolves only the state-independent shapes: a bare {@link ServerPacket} or a {@link LazyPacket}. NEVER route these
 * paths through {@code extractServerPacket}: its {@code CachedPacket} branch forces that packet's single, stateless
 * {@code FramedPacket} cache for whatever connection state is passed - a hardcoded PLAY poisons the cache during a
 * modern client's CONFIGURATION join and corrupts the real config send. None of the rewritten packets are ever
 * CachedPackets, so skipping that shape loses no coverage.
 */
public final class PacketShapes {

    private PacketShapes() {}

    /** The unwrapped {@link ServerPacket}, or {@code null} for shapes that can't be resolved statelessly. */
    public static @Nullable ServerPacket unwrapStateless(@NotNull SendablePacket packet) {
        return switch (packet) {
            case ServerPacket sp -> sp;
            case LazyPacket lazy -> lazy.packet();
            default -> null;
        };
    }
}
