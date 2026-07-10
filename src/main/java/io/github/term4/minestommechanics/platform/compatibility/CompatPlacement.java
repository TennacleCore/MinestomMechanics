package io.github.term4.minestommechanics.platform.compatibility;

import io.github.term4.minestommechanics.MinestomMechanics;
import io.github.term4.minestommechanics.platform.player.OptimizedPlayer;
import io.github.term4.minestommechanics.world.MechanicsWorld;
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
 * <p><b>Reach ({@code blockPlaceReach}):</b> cancels a placement whose clicked point is farther than the reach from the
 * player's <em>server</em> eye (the 1.8 preset under {@code legacyHitbox}: 1.54 sneaking vs modern 1.27 crouch). Closes the
 * modern sneak-bridge over-reach (the lower crouch eye out-reaches 1.8). Only modern, non-Animatium clients need it (the only
 * eye that under-shoots 1.8); skipped for legacy, Animatium-{@code old_sneak_height}, and creative/spectator clients (they already aim correctly).
 *
 * <p><b>Air placement ({@code oldPlacement}):</b> refuses a placement whose clicked cell is air - the server half of the 1.8
 * "don't place against air" rule (Animatium enforces the client half via {@code OLD_PLACEMENT}). A live raycast only block-hits
 * a <em>solid</em> cell, so an air target = the client aimed at a cell it just broke (the creative "quick replace" floating block). Catches any non-Animatium client that still sends it.
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
        // the event's block is the base-map read; a virtual-world member's clicked block may exist only in their world
        if (op.compat().oldPlacement() && MechanicsWorld.viewed(op).getBlock(event.getBlockPosition()).isAir()) {
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
