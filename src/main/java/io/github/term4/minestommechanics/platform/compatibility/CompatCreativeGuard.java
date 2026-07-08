package io.github.term4.minestommechanics.platform.compatibility;

import io.github.term4.minestommechanics.MinestomMechanics;
import io.github.term4.minestommechanics.platform.player.OptimizedPlayer;
import net.minestom.server.event.EventFilter;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.inventory.CreativeInventoryActionEvent;
import net.minestom.server.event.trait.PlayerEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Seals the {@code attack_range} stamp against the one path that leaks it into server state. The stamp
 * ({@link CompatState#stampAttackRange}) rewrites only the client's VIEW of held items - the server item stays clean.
 * Creative slots are client-authoritative, so a stamped client echoes the stamped item back
 * ({@link CreativeInventoryActionEvent}) and the server would store the phantom component, from where it spreads to
 * drops and other viewers. Strips it back off ({@link CompatState#sanitizeInboundItem}) on the creative set + drop;
 * inert for clients that aren't stamped. Needs the {@link OptimizedPlayer} provider.
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
