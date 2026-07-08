package io.github.term4.minestommechanics.vri;

import net.kyori.adventure.sound.Sound;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.item.PickupItemEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.sound.SoundEvent;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Player item pickup - Minestom fires {@link PickupItemEvent} + the animation but adds nothing to the inventory.
 * Adds the stack (cancels when full, so the item stays), vanilla pop sound; non-player pickups untouched.
 */
public final class ItemPickup {

    private ItemPickup() {}

    public static void install(EventNode<@NotNull Event> node) {
        node.addListener(PickupItemEvent.class, e -> {
            if (!(e.getLivingEntity() instanceof Player player)) return;
            if (!player.getInventory().addItemStack(e.getItemStack())) {
                e.setCancelled(true);
                return;
            }
            Instance instance = player.getInstance();
            if (instance == null) return;
            var rnd = ThreadLocalRandom.current();
            float pitch = ((rnd.nextFloat() - rnd.nextFloat()) * 0.7f + 1.0f) * 2.0f;
            Pos at = e.getItemEntity().getPosition();
            instance.playSound(Sound.sound(SoundEvent.ENTITY_ITEM_PICKUP.key(), Sound.Source.PLAYER, 0.2f, pitch),
                    at.x(), at.y(), at.z());
        });
    }
}
