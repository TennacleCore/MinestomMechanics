package io.github.term4.minestommechanics.platform.compatibility;

import io.github.term4.minestommechanics.MinestomMechanics;
import io.github.term4.minestommechanics.platform.player.OptimizedPlayer;
import net.minestom.server.coordinate.Point;
import net.minestom.server.entity.Player;
import net.minestom.server.event.EventFilter;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.PlayerBlockPlaceEvent;
import net.minestom.server.event.trait.PlayerEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Block-placement reach restriction ({@code CompatConfig.blockPlaceReach}): cancels a placement whose clicked point is
 * farther than the configured reach from the player's <em>server</em> eye ({@code OptimizedPlayer.getEyeHeight} - the 1.8
 * preset under {@code legacyHitbox}: 1.54 sneaking vs the modern 1.27 crouch eye). Closes the modern sneak-bridge over-reach,
 * where the lower crouch eye lets a modern client place blocks a 1.8 player couldn't. Installed once when the player provider
 * is on; inert unless the player's config sets a reach.
 */
public final class CompatPlacement {

    private CompatPlacement() {}

    /** Installs the placement-reach listener. Inert unless a player's {@code CompatConfig.blockPlaceReach} is set. */
    public static void install(MinestomMechanics mm) {
        EventNode<@NotNull PlayerEvent> node = EventNode.type("mm:compat-placement", EventFilter.PLAYER);
        node.addListener(PlayerBlockPlaceEvent.class, CompatPlacement::onPlace);
        mm.install(node);
    }

    private static void onPlace(PlayerBlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (!(player instanceof OptimizedPlayer op)) return;
        Double reach = op.compat().blockPlaceReach();
        if (reach == null) return;
        // exact clicked point = the support block (one step back along the clicked face) + the cursor offset on its face
        Point hit = event.getBlockPosition().relative(event.getBlockFace().getOppositeFace()).add(event.getCursorPosition());
        Point eye = player.getPosition().add(0, player.getEyeHeight(), 0); // value (b) server eye = 1.8 preset under legacyHitbox
        if (eye.distanceSquared(hit) > reach * reach) event.setCancelled(true);
    }
}
