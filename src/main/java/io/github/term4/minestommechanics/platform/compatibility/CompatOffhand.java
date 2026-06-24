package io.github.term4.minestommechanics.platform.compatibility;

import io.github.term4.minestommechanics.MinestomMechanics;
import io.github.term4.minestommechanics.platform.player.OptimizedPlayer;
import net.minestom.server.entity.Player;
import net.minestom.server.event.EventFilter;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.inventory.InventoryPreClickEvent;
import net.minestom.server.event.player.PlayerSwapItemEvent;
import net.minestom.server.event.trait.PlayerEvent;
import net.minestom.server.inventory.PlayerInventory;
import net.minestom.server.inventory.click.Click;
import org.jetbrains.annotations.NotNull;

/**
 * Server-side offhand disabling ({@code CompatConfig.disableOffhand}): blocks a modern client from putting items in / using
 * the offhand that 1.8 lacks. Cancels the gameplay F-swap ({@link PlayerSwapItemEvent} - the client only sends it, never
 * predicts the swap, so a cancel is an invisible no-op) and any inventory click that targets the offhand slot (the in-GUI
 * F-swap, or a direct click/drag/hotbar-swap on slot {@value #OFFHAND_SLOT}; Minestom resyncs the GUI on a cancelled click).
 * Installed once when the player provider is on; inert unless the player's config enables it. 1.8 clients have no offhand,
 * so this only affects modern clients.
 */
public final class CompatOffhand {

    /** The offhand window slot in the player inventory (Minestom {@code PlayerInventoryUtils.OFFHAND_SLOT}). */
    private static final int OFFHAND_SLOT = 45;

    private CompatOffhand() {}

    /** Installs the offhand-disable listeners. Inert unless a player's {@code CompatConfig.disableOffhand} is on. */
    public static void install(MinestomMechanics mm) {
        EventNode<@NotNull PlayerEvent> node = EventNode.type("mm:compat-offhand", EventFilter.PLAYER);
        node.addListener(PlayerSwapItemEvent.class, e -> { if (disabled(e.getPlayer())) e.setCancelled(true); });
        node.addListener(InventoryPreClickEvent.class, e -> { if (disabled(e.getPlayer()) && targetsOffhand(e)) e.setCancelled(true); });
        mm.install(node);
    }

    private static boolean disabled(Player player) {
        return player instanceof OptimizedPlayer op && op.compat().disableOffhand();
    }

    // TODO(legacy-offhand-emulation) STUB - the inverse of disableOffhand: give a 1.8 client (which has no native offhand)
    // a usable one. Mechanism undecided (per user). Design space, to fill in once chosen:
    //   (a) server-tracked virtual offhand item + a swap trigger 1.8 can actually send - a /offhand command, or a hotbar-slot
    //       convention (1.8 can't send SWAP_ITEM_WITH_OFFHAND), reconciled here;
    //   (b) render the emulated offhand item to the 1.8 client via an equipment slot it shows;
    //   (c) auto-apply the offhand item on the action it backs (totem-on-lethal, shield-on-block) without a real slot.
    // Gate with a new CompatConfig.emulateOffhand knob -> CompatState, enforced from this listener (a legacy-protocol check
    // via ClientInfoTracker, since this only applies to clients without a native offhand). Wire once the mechanism is picked.

    /** Whether the click moves an item into/out of the offhand: the F-swap (any inventory), or slot {@value #OFFHAND_SLOT} in the player's own inventory. */
    private static boolean targetsOffhand(InventoryPreClickEvent e) {
        Click click = e.getClick();
        if (click instanceof Click.OffhandSwap) return true; // F while an inventory is open - always swaps with the offhand
        if (!(e.getInventory() instanceof PlayerInventory)) return false; // slot 45 is the offhand only in the player's own inventory
        return switch (click) {
            case Click.HotbarSwap hs -> hs.slot() == OFFHAND_SLOT;
            case Click.Drag d -> d.slots().contains(OFFHAND_SLOT);
            default -> click.slot() == OFFHAND_SLOT;
        };
    }
}
