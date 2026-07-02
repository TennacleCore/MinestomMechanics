package io.github.term4.minestommechanics.mechanics.projectile.types;

import io.github.term4.minestommechanics.MinestomMechanics;
import io.github.term4.minestommechanics.mechanics.projectile.ProjectileSnapshot;
import io.github.term4.minestommechanics.mechanics.projectile.ProjectileSystem;
import net.kyori.adventure.key.Key;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.PlayerHand;
import net.minestom.server.event.EventFilter;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.PlayerUseItemEvent;
import net.minestom.server.event.player.PlayerUseItemOnBlockEvent;
import net.minestom.server.event.trait.PlayerEvent;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Base for a hand-thrown item projectile (snowball, egg, ender pearl): using the {@link #material} launches one and
 * consumes it (unless creative). Subclasses add only their flight entity + impact behavior; the throw/consume wiring lives here.
 */
public abstract class ThrowableItemType extends ProjectileType {

    private final Material material;
    private @Nullable EventNode<@NotNull PlayerEvent> node;
    private @Nullable ProjectileSystem system;

    protected ThrowableItemType(Key key, String name, EntityType entityType, Material material) {
        super(key, name, entityType);
        this.material = material;
    }

    /** The item that throws this projectile. */
    public Material material() { return material; }

    @Override
    public void enable(ProjectileSystem system, MinestomMechanics mm) {
        this.system = system;
        EventNode<@NotNull PlayerEvent> n = EventNode.type("mm:" + key().value(), EventFilter.PLAYER);
        n.addListener(PlayerUseItemEvent.class, e -> throwItem(e.getPlayer(), e.getHand(), e.getItemStack()));
        // An item with a block action (fire charge - lights fire) makes the client send use_item_on_block, not use_item,
        // when aimed at a block, so the throw must handle both. Snowball/egg/pearl have no block action and only ever fire use_item.
        n.addListener(PlayerUseItemOnBlockEvent.class, e -> throwItem(e.getPlayer(), e.getHand(), e.getItemStack()));
        system.node().addChild(n);
        node = n;
    }

    private void throwItem(Player p, PlayerHand hand, ItemStack item) {
        if (item.material() != material || system == null) return;
        system.launch(ProjectileSnapshot.of(p, this).withItem(item));
        if (p.getGameMode() != GameMode.CREATIVE) {
            p.setItemInHand(hand, item.withAmount(item.amount() - 1));
        }
    }

    @Override
    public void disable() {
        if (system != null && node != null) system.node().removeChild(node);
        node = null;
    }
}
