package io.github.term4.minestommechanics.api.event.blocking;

import io.github.term4.minestommechanics.world.MechanicsWorld;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.PlayerHand;
import net.minestom.server.event.trait.CancellableEvent;
import net.minestom.server.item.ItemStack;

/**
 * Fired when a player begins blocking (raises a blockable item). Cancel to deny the block (never enters the state).
 * A shield only becomes effective after its block-delay; per-hit reduction is {@link BlockingDamageEvent}, the lower is
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

    public Player player() { return player; }
    /** The player's gameplay world. */
    public MechanicsWorld world() { return MechanicsWorld.of(player()); }
    public PlayerHand hand() { return hand; }
    public ItemStack item() { return item; }

    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancel) { this.cancelled = cancel; }
}
