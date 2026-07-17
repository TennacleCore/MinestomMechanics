package io.github.term4.minestommechanics.platform.compatibility;

import io.github.term4.minestommechanics.MinestomMechanics;
import io.github.term4.minestommechanics.platform.player.OptimizedPlayer;
import net.minestom.server.event.EventFilter;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.inventory.CreativeInventoryActionEvent;
import net.minestom.server.event.trait.PlayerEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Seals the client-view item rewrites ({@link CompatState#rewriteItems}) against the one path that leaks them into
 * server state: creative slots are client-authoritative, so an affected client echoes the rewritten item back
 * ({@link CreativeInventoryActionEvent}) and the server would store the phantom, from where it spreads to drops and
 * other viewers. {@link CompatState#sanitizeInboundItem} undoes it on the creative set + drop; inert for unaffected
 * clients. Needs the {@link OptimizedPlayer} provider.
 */
public final class CompatCreativeGuard {

    private CompatCreativeGuard() {}

    public static void install(MinestomMechanics mm) {
        EventNode<@NotNull PlayerEvent> node = EventNode.type("mm:compat-creative-guard", EventFilter.PLAYER);
        node.addListener(CreativeInventoryActionEvent.class, e -> {
            if (e.getPlayer() instanceof OptimizedPlayer op) e.setClickedItem(op.compat().sanitizeInboundItem(e.getClickedItem()));
        });
        mm.install(node);
    }
}
