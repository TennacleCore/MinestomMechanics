package io.github.term4.minestommechanics.mechanics.damage.silent;

import io.github.term4.minestommechanics.MinestomMechanics;
import net.minestom.server.entity.Player;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.player.PlayerPacketOutEvent;
import net.minestom.server.network.packet.server.play.EntityAttributesPacket;
import net.minestom.server.network.packet.server.play.UpdateHealthPacket;
import net.minestom.server.tag.Tag;
import org.jetbrains.annotations.NotNull;

/**
 * Suppresses outgoing health packets while {@link SilentDamage} applies a silent (no-hurt) health
 * change to a legacy client. While the per-player tag is set, the player's own {@code UpdateHealth}
 * and {@code EntityAttributes} packets are cancelled, so health is delivered via entity metadata
 * instead (which does not trigger the 1.8 hurt-cam tilt).
 */
public final class HurtSuppression {

    private static final Tag<Boolean> SUPPRESS = Tag.Transient("mm:suppress-health-packets");
    private static volatile boolean installed = false;

    private HurtSuppression() {}

    /** Installs the packet-out listener once. Safe to call from every {@code DamageSystem.install}. */
    public static synchronized void install(MinestomMechanics mm) {
        if (installed) return;
        installed = true;

        EventNode<@NotNull Event> node = EventNode.all("mm:hurt-suppression");
        node.addListener(PlayerPacketOutEvent.class, e -> {
            Player player = e.getPlayer();
            if (!Boolean.TRUE.equals(player.getTag(SUPPRESS))) return;
            var packet = e.getPacket();
            if (packet instanceof UpdateHealthPacket) {
                e.setCancelled(true);
            } else if (packet instanceof EntityAttributesPacket attr && attr.entityId() == player.getEntityId()) {
                e.setCancelled(true);
            }
        });
        node.addListener(PlayerDisconnectEvent.class, e -> e.getPlayer().removeTag(SUPPRESS));
        mm.install(node);
    }

    /** Toggle suppression of health packets for a player (set before setHealth, clear after). */
    public static void setSuppressHealthPackets(Player player, boolean suppress) {
        if (suppress) {
            player.setTag(SUPPRESS, true);
        } else {
            player.removeTag(SUPPRESS);
        }
    }
}
