package io.github.term4.minestommechanics.platform.fixes.client;

import net.minestom.server.MinecraftServer;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.PlayerPacketEvent;
import net.minestom.server.network.packet.client.play.ClientTabCompletePacket;
import net.minestom.server.network.packet.server.play.TabCompletePacket;

import java.util.Arrays;
import java.util.List;

/**
 * Command-name tab completion for legacy clients: answers a tab-complete request whose cursor is still in the command
 * name (no space yet) with the registered command names matching the prefix.
 *
 * <p><b>Why:</b> a 1.8 client asks the server to complete command names (modern clients do it client-side from the
 * command tree). ViaVersion forwards the request, but Minestom's {@code TabCompleteListener} only suggests argument
 * values, so command names never complete. Vanilla answers it because Brigadier's literal nodes self-suggest.
 *
 * <p>Registered on the fixes node by {@code FixesSystem.install} when enabled. Not gated on protocol version: a modern
 * client never sends a no-space request, and argument requests (which carry a space) fall through to Minestom.
 *
 * <p><b>Temporary:</b> removable once the upstream fix (branch {@code fix/tab-complete-command-names}) is on the pinned dependency.
 */
public final class LegacyTabCompleteFix {

    private LegacyTabCompleteFix() {}

    /** Registers the command-name completion listener on {@code node}. Called by {@code FixesSystem.install} when enabled. */
    public static void install(EventNode<Event> node) {
        node.addListener(PlayerPacketEvent.class, event -> {
            if (!(event.getPacket() instanceof ClientTabCompletePacket packet)) return;
            final String text = packet.text();
            final String prefix = text.startsWith("/") ? text.substring(1) : text;
            if (prefix.indexOf(' ') >= 0) return; // an argument is being typed - Minestom's listener handles that
            final List<TabCompletePacket.Match> matches = MinecraftServer.getCommandManager().getCommands().stream()
                    .flatMap(command -> Arrays.stream(command.getNames()))
                    .filter(name -> name.regionMatches(true, 0, prefix, 0, prefix.length()))
                    .distinct().sorted()
                    .map(name -> new TabCompletePacket.Match(name, null))
                    .toList();
            if (matches.isEmpty()) return;
            event.getPlayer().sendPacket(new TabCompletePacket(packet.transactionId(), 1, prefix.length(), matches));
        });
    }
}
