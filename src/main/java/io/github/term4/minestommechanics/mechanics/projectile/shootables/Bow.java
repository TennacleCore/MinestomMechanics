package io.github.term4.minestommechanics.mechanics.projectile.shootables;

import io.github.term4.minestommechanics.mechanics.projectile.ProjectileSnapshot;
import io.github.term4.minestommechanics.mechanics.projectile.ProjectileSystem;
import io.github.term4.minestommechanics.mechanics.projectile.entities.ArrowEntity;
import io.github.term4.minestommechanics.mechanics.projectile.entities.ProjectileEntity;
import io.github.term4.minestommechanics.mechanics.projectile.types.Arrow;
import io.github.term4.minestommechanics.mechanics.projectile.types.ProjectileType;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.item.PlayerCancelItemUseEvent;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import org.jetbrains.annotations.NotNull;

/**
 * Bow launcher ({@link Shootable}): a drawn-bow release fires an arrow. The bow ITEM and the {@link Arrow} PROJECTILE
 * are distinct concerns - the arrow type stays pure identity, the bow lives here. Pass {@code new Bow()} to
 * {@link ProjectileSystem#install}.
 *
 * <p>Vanilla 1.8 {@code ItemBow} on release: draw power {@code (s^2 + 2s)/3} (s = draw seconds) capped at 1,
 * {@code < 0.1} fires nothing, full draw ({@code == 1}) is critical; consumes one arrow (unless creative); the arrow
 * launches at speed {@code power * 3} (the arrow config's {@code speed} × {@code power}). TODO: gate the draw start on
 * having arrows, offhand-first arrow selection, Infinity, Power/Punch/Flame enchants.
 */
public final class Bow implements Shootable {

    /** Minimum draw power that fires (vanilla: {@code < 0.1} releases nothing). */
    private static final float MIN_POWER = 0.1f;

    private final ProjectileType arrowType;

    /** A bow that fires the built-in {@link Arrow}. */
    public Bow() { this(Arrow.INSTANCE); }

    /** A bow that fires a custom arrow type (its entity must be an {@link ArrowEntity} for crit/pickup wiring). */
    public Bow(ProjectileType arrowType) { this.arrowType = arrowType; }

    @Override
    public void install(@NotNull EventNode<@NotNull Event> node, @NotNull ProjectileSystem system) {
        node.addListener(PlayerCancelItemUseEvent.class, e -> onRelease(e, system));
    }

    private void onRelease(PlayerCancelItemUseEvent e, ProjectileSystem system) {
        if (e.getItemStack().material() != Material.BOW) return;
        Player p = e.getPlayer();
        float power = drawPower(e.getUseDuration());
        if (power < MIN_POWER) return; // too short a draw - no shot
        boolean creative = p.getGameMode() == GameMode.CREATIVE;
        if (!creative && !consumeArrow(p)) return; // no arrow to fire
        ProjectileEntity proj = system.launch(ProjectileSnapshot.of(p, arrowType).withPower(power).withItem(e.getItemStack()));
        if (proj instanceof ArrowEntity arrow) {
            arrow.setCritical(power >= 1f);
            // Survival shot -> ALLOWED (collector keeps the arrow); creative shot -> CREATIVE_ONLY (no item).
            arrow.setPickup(creative ? ArrowEntity.Pickup.CREATIVE_ONLY : ArrowEntity.Pickup.ALLOWED);
        }
    }

    /** Vanilla bow power curve: {@code f = ticks/20; (f*f + 2f)/3}, capped at 1. */
    private static float drawPower(long useDurationTicks) {
        float f = useDurationTicks / 20.0f;
        float power = (f * f + 2 * f) / 3.0f;
        return power > 1f ? 1f : power;
    }

    /** Removes one arrow from the player's inventory (first {@link Material#ARROW} found); false if none. */
    private static boolean consumeArrow(Player p) {
        var inv = p.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack stack = inv.getItemStack(i);
            if (stack.material() == Material.ARROW) {
                inv.setItemStack(i, stack.withAmount(stack.amount() - 1));
                return true;
            }
        }
        return false;
    }
}
