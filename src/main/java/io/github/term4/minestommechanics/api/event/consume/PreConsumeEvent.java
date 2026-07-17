package io.github.term4.minestommechanics.api.event.consume;

import io.github.term4.minestommechanics.api.event.CancellableMechanicsEvent;
import io.github.term4.minestommechanics.Services;
import io.github.term4.minestommechanics.mechanics.consumable.Consumable;
import io.github.term4.minestommechanics.mechanics.consumable.ConsumableConfigResolver.ConsumableContext;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.PlayerHand;
import net.minestom.server.item.ItemStack;

/**
 * The pre-consume gate: fired the moment an enabled consumable use STARTS, before the {@code canConsume} gate and the
 * use timer. Cancel and the use never begins (no eating animation, no timer).
 */
public final class PreConsumeEvent extends CancellableMechanicsEvent<ConsumableContext> {

    public PreConsumeEvent(ConsumableContext ctx, Services services) {
        super(ctx, services);
    }

    public Player user() { return finalSnap().user(); }
    public ItemStack item() { return finalSnap().item(); }
    public PlayerHand hand() { return finalSnap().hand(); }
    /** The resolved type - a registered {@link Consumable} or the component-food floor. */
    public Consumable consumable() { return finalSnap().consumable(); }
}
