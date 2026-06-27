package io.github.term4.minestommechanics.api.event;

import net.minestom.server.entity.Player;
import net.minestom.server.entity.PlayerHand;
import net.minestom.server.event.Event;
import net.minestom.server.item.ItemStack;

/**
 * Fired when a player stops blocking - lowers the item (released, finished, or interrupted e.g. by a held-slot switch).
 * Informational counterpart to {@link BlockingStartEvent}.
 */
public record BlockingStopEvent(Player player, PlayerHand hand, ItemStack item) implements Event {}
