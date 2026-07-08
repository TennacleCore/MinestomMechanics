package io.github.term4.minestommechanics.mechanics.projectile.shootables;

import io.github.term4.minestommechanics.MinestomMechanics;
import io.github.term4.minestommechanics.mechanics.durability.DurabilitySystem;
import io.github.term4.minestommechanics.mechanics.projectile.ProjectileSnapshot;
import io.github.term4.minestommechanics.mechanics.projectile.ProjectileSystem;
import io.github.term4.minestommechanics.mechanics.projectile.entities.FishingBobberEntity;
import io.github.term4.minestommechanics.mechanics.projectile.types.FishingBobber;
import io.github.term4.minestommechanics.mechanics.projectile.types.ProjectileType;
import net.minestom.server.entity.EquipmentSlot;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.PlayerHand;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.PlayerUseItemEvent;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import org.jetbrains.annotations.NotNull;

/**
 * Fishing rod launcher ({@link Shootable}): a rod use casts a {@link FishingBobber}, a second use retrieves it (reel
 * pull + rod durability). PvP rod only - no fishing loot. Pass {@code new FishingRod()} to {@link ProjectileSystem#install}.
 */
public final class FishingRod implements Shootable {

    private final ProjectileType bobberType;

    /** A rod that casts the built-in {@link FishingBobber}. */
    public FishingRod() { this(FishingBobber.INSTANCE); }

    /** A rod casting a custom bobber type (its entity must be a {@link FishingBobberEntity} for hook/retrieve wiring). */
    public FishingRod(ProjectileType bobberType) { this.bobberType = bobberType; }

    @Override
    public void install(@NotNull EventNode<@NotNull Event> node, @NotNull ProjectileSystem system) {
        // use_item ONLY: a click at a block in reach sends use_item_on FOLLOWED by use_item (the rod has no block
        // action), and vanilla acts on the latter alone - handling both toggles cast+retrieve twice per click
        node.addListener(PlayerUseItemEvent.class, e -> onUse(e.getPlayer(), e.getHand(), e.getItemStack(), system));
    }

    private void onUse(Player p, PlayerHand hand, ItemStack item, ProjectileSystem system) {
        if (item.material() != Material.FISHING_ROD) return;
        FishingBobberEntity active = p.getTag(FishingBobberEntity.ACTIVE_BOBBER);
        if (active != null && !active.isRemoved()) {
            // same-tick spawn+destroy ghosts on the ViaRewind held-spawn path; ignore a retract under 1 tick old
            if (active.getAliveTicks() < 1) return;
            damageRod(p, hand, active.retrieve());
        } else {
            var proj = system.launch(ProjectileSnapshot.of(p, bobberType).withItem(item));
            if (proj instanceof FishingBobberEntity bobber) p.setTag(FishingBobberEntity.ACTIVE_BOBBER, bobber);
        }
    }

    private static void damageRod(Player p, PlayerHand hand, int amount) {
        if (amount <= 0) return;
        var mm = MinestomMechanics.getInstance();
        DurabilitySystem durability = mm.isInitialized() ? mm.services().durability() : null;
        if (durability != null) {
            durability.damage(p, hand == PlayerHand.MAIN ? EquipmentSlot.MAIN_HAND : EquipmentSlot.OFF_HAND, amount);
        }
    }
}
