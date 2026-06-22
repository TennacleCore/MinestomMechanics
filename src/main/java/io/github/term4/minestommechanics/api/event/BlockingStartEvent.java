package io.github.term4.minestommechanics.api.event;

import net.minestom.server.entity.Player;
import net.minestom.server.entity.PlayerHand;
import net.minestom.server.event.trait.CancellableEvent;
import net.minestom.server.item.ItemStack;

/**
 * Fired when a player begins blocking - raises a blockable item (right-click). <b>Cancellable:</b> cancelling denies the
 * block, so the player never enters the blocking state. For a shield the raise is immediate but the block only becomes
 * effective after its block-delay; the actual per-hit reduction fires {@link BlockingDamageEvent}, and the lower fires
 * {@link BlockingStopEvent}.
 */
public final class BlockingStartEvent implements CancellableEvent {

    private final Player player;
    private final PlayerHand hand;
    private final ItemStack item;
    private boolean cancelled;

    public BlockingStartEvent(Player player, PlayerHand hand, ItemStack item) {
        this.player = player;
        this.hand = hand;
        this.item = item;
    }

    /** The blocking player. */
    public Player player() { return player; }
    /** The hand holding the blocking item. */
    public PlayerHand hand() { return hand; }
    /** The item being raised to block. */
    public ItemStack item() { return item; }

    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancel) { this.cancelled = cancel; }
}
