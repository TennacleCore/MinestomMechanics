package io.github.term4.minestommechanics.api.event;

import net.minestom.server.entity.Player;
import net.minestom.server.entity.PlayerHand;
import net.minestom.server.event.Event;
import net.minestom.server.item.ItemStack;

/**
 * Fired when a player stops blocking - lowers the item (released right-click, finished the use, or the use was
 * interrupted, e.g. switching held slot). Informational counterpart to {@link BlockingStartEvent}.
 *
 * @param player the player who stopped blocking
 * @param hand   the hand that held the blocking item
 * @param item   the item that was being used to block
 */
public record BlockingStopEvent(Player player, PlayerHand hand, ItemStack item) implements Event {}
