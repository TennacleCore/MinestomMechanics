package io.github.term4.minestommechanics.platform.compatibility;

import io.github.term4.minestommechanics.MinestomMechanics;
import io.github.term4.minestommechanics.platform.player.OptimizedPlayer;
import io.github.term4.minestommechanics.tracking.ClientInfoTracker;
import net.minestom.server.coordinate.Point;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.event.EventFilter;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.PlayerBlockInteractEvent;
import net.minestom.server.event.player.PlayerBlockPlaceEvent;
import net.minestom.server.event.trait.PlayerEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Server-side 1.8 block-placement rules. Two independent restrictions, each gated by its own {@code CompatConfig} knob:
 *
 * <p><b>Reach ({@code blockPlaceReach}):</b> cancels a placement whose clicked point is farther than the configured reach
 * from the player's <em>server</em> eye ({@code OptimizedPlayer.getEyeHeight} - the 1.8 preset under {@code legacyHitbox}:
 * 1.54 sneaking vs the modern 1.27 crouch eye). Closes the modern sneak-bridge over-reach, where the lower crouch eye lets a
 * modern client place blocks a 1.8 player couldn't. Only needed for <em>modern, non-Animatium</em> clients - the only ones
 * whose client eye (1.27) under-shoots the 1.8 eye; skipped for everyone who already aims correctly to avoid cancelling legit
 * placements: <b>legacy (1.8) clients</b> (aim from the 1.8 eye), <b>Animatium clients with {@code old_sneak_height}</b> (eye
 * corrected client-side), <b>creative / spectator</b> (extra reach by design; spectators can't place).
 *
 * <p><b>Air placement ({@code oldPlacement}):</b> refuses a placement whose clicked cell is air - the server half of the 1.8
 * "don't place against air" rule (Animatium enforces the client half via {@code OLD_PLACEMENT}). A live raycast only block-hits
 * a <em>solid</em> cell, so an air target means the client aimed at a cell it just broke: the creative "quick replace" that
 * drops a block back into the emptied spot (it floats). Catches any non-Animatium / modified client that still sends it.
 *
 * <p>Installed once when the player provider is on; each rule is inert unless the player's config enables it.
 */
public final class CompatPlacement {

    private CompatPlacement() {}

    /** Installs the placement listeners. Each rule is inert unless the player's {@code CompatConfig} enables it. */
    public static void install(MinestomMechanics mm) {
        EventNode<@NotNull PlayerEvent> node = EventNode.type("mm:compat-placement", EventFilter.PLAYER);
        // resolve the client-info tracker lazily - install runs before it's created in init()
        node.addListener(PlayerBlockPlaceEvent.class, e -> onPlace(e, mm.clientInfo()));
        node.addListener(PlayerBlockInteractEvent.class, CompatPlacement::onInteract);
        mm.install(node);
    }

    /** 1.8 refused to place against an air cell. An air clicked-block (a live raycast never block-hits air) = the client aimed at a cell it just broke - block the item use to refuse the floating quick-replace. */
    private static void onInteract(PlayerBlockInteractEvent event) {
        if (!(event.getPlayer() instanceof OptimizedPlayer op)) return;
        if (op.compat().oldPlacement() && event.getBlock().isAir()) {
            event.setBlockingItemUse(true);
        }
    }

    private static void onPlace(PlayerBlockPlaceEvent event, ClientInfoTracker clientInfo) {
        Player player = event.getPlayer();
        if (!(player instanceof OptimizedPlayer op)) return;
        Double reach = op.compat().blockPlaceReach();
        if (reach == null) return;
        // Only modern non-Animatium survival clients can sneak-bridge past 1.8 reach; everyone else already aims correctly.
        if (clientInfo.isLegacy(player)
                || op.compat().handlesNatively(AnimatiumFeature.OLD_SNEAK_HEIGHT)
                || player.getGameMode() == GameMode.CREATIVE
                || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        // exact clicked point = the support block (one step back along the clicked face) + the cursor offset on its face
        Point hit = event.getBlockPosition().relative(event.getBlockFace().getOppositeFace()).add(event.getCursorPosition());
        Point eye = player.getPosition().add(0, player.getEyeHeight(), 0); // value (b) server eye = 1.8 preset under legacyHitbox
        if (eye.distanceSquared(hit) > reach * reach) event.setCancelled(true);
    }
}
