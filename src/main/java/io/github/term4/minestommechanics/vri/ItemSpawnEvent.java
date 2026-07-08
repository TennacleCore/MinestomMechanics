package io.github.term4.minestommechanics.vri;

import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.event.trait.CancellableEvent;
import net.minestom.server.instance.Instance;

/**
 * Fired before a VRI drop enters the instance ({@link DroppedItemEntity#spawn}). The entity is live - adjust its
 * stack, pickup delay or velocity here. Cancel to drop nothing.
 */
public final class ItemSpawnEvent implements CancellableEvent {

    public enum Cause { BLOCK_DROP, PLAYER_DROP }

    private final DroppedItemEntity item;
    private final Cause cause;
    private final Player player;
    private final Instance instance;
    private final Pos position;
    private boolean cancelled;

    public ItemSpawnEvent(DroppedItemEntity item, Cause cause, Player player, Instance instance, Pos position) {
        this.item = item;
        this.cause = cause;
        this.player = player;
        this.instance = instance;
        this.position = position;
    }

    public DroppedItemEntity item() { return item; }
    public Cause cause() { return cause; }
    /** The player responsible (breaker / dropper). */
    public Player player() { return player; }
    public Instance instance() { return instance; }
    public Pos position() { return position; }

    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancel) { this.cancelled = cancel; }
}
