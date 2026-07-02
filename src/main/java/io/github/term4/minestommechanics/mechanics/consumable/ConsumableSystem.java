package io.github.term4.minestommechanics.mechanics.consumable;

import io.github.term4.minestommechanics.MechanicsKeys;
import io.github.term4.minestommechanics.MechanicsModule;
import io.github.term4.minestommechanics.MinestomMechanics;
import io.github.term4.minestommechanics.Services;
import io.github.term4.minestommechanics.mechanics.consumable.ConsumableConfigResolver.ConsumableContext;
import io.github.term4.minestommechanics.mechanics.consumable.ConsumableConfigResolver.ResolvedConsumable;
import io.github.term4.minestommechanics.util.tick.TickPhase;
import io.github.term4.minestommechanics.util.tick.TickSystem;
import net.minestom.server.component.DataComponents;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.PlayerHand;
import net.minestom.server.event.EventFilter;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.item.PlayerCancelItemUseEvent;
import net.minestom.server.event.item.PlayerFinishItemUseEvent;
import net.minestom.server.event.player.PlayerUseItemEvent;
import net.minestom.server.event.trait.PlayerEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Consumable items (food / drink) eaten over time. Mirrors the other type-registry systems (projectiles / damage). The
 * use lifecycle maps onto Minestom's seams - {@link PlayerUseItemEvent} (start) -&gt; {@link PlayerFinishItemUseEvent}
 * (completed) / {@link PlayerCancelItemUseEvent} (released early) - plus a per-tick "during" pass off {@link TickSystem}.
 * Each phase resolves the scope's {@link ConsumableTypeConfig}. Minestom fires the finish but applies no effects and doesn't consume the item, so that payload is ours.
 *
 * <p><b>Protocol fix.</b> Minestom derives the use duration from the modern {@code consumable} component, which a 1.8/Via
 * client never carries - so it comes back {@code 0} and the finish never fires. We set it from the resolved
 * {@link ResolvedConsumable#consumeTicks()} in the start handler, making completion server-authoritative and version-independent.
 */
public final class ConsumableSystem implements MechanicsModule {

    private final MinestomMechanics mm;
    private final Services services;
    private final ConsumableConfig config;
    private final EventNode<@NotNull PlayerEvent> node;
    private final ConsumableRegistry registry = new ConsumableRegistry();
    /** The "during" tick is installed once for the JVM ({@link TickSystem} has no removal); it reads the live system each tick. */
    private static final AtomicBoolean TICK_HOOK = new AtomicBoolean();

    public ConsumableSystem(MinestomMechanics mm, ConsumableConfig config) {
        this.mm = mm;
        this.services = mm.services();
        this.config = config;
        this.node = EventNode.type("mm:consumable", EventFilter.PLAYER);
        node.addListener(PlayerUseItemEvent.class, this::onUse);
        node.addListener(PlayerFinishItemUseEvent.class, this::onFinish);
        node.addListener(PlayerCancelItemUseEvent.class, this::onCancel);
    }

    public EventNode<@NotNull PlayerEvent> node() { return node; }
    public ConsumableConfig config() { return config; }
    public ConsumableRegistry registry() { return registry; }

    /** Registers a consumable type (built-in or custom). */
    public ConsumableSystem register(Consumable consumable) { registry.register(consumable); return this; }

    /** Effective config for {@code subject}: the scoped profile, else the install config. */
    public ConsumableConfig configFor(@Nullable Entity subject) {
        ConsumableConfig scoped = mm.profiles().resolve(subject, MechanicsKeys.CONSUMABLES);
        return scoped != null ? scoped : config;
    }

    /** Resolves the effective values for {@code player} consuming {@code item} in {@code hand}, or {@code null} if {@code item} is not a registered consumable. */
    private @Nullable Resolution resolve(Player player, PlayerHand hand, ItemStack item) {
        Consumable c = registry.forMaterial(item.material());
        if (c == null) return null;
        ConsumableContext ctx = new ConsumableContext(player, item, hand, c, services);
        return new Resolution(ctx, ConsumableConfigResolver.resolve(configFor(player), ctx));
    }

    private record Resolution(ConsumableContext ctx, ResolvedConsumable resolved) {}

    private void onUse(PlayerUseItemEvent e) {
        Resolution r = resolve(e.getPlayer(), e.getHand(), e.getItemStack());
        if (r == null || !r.resolved.enabled()) return;
        // Protocol fix: drive the duration ourselves so the finish fires regardless of the item's component / client version.
        e.setItemUseTime(r.resolved.consumeTicks());
        r.resolved.behavior().onStart(r.ctx);
    }

    private void onFinish(PlayerFinishItemUseEvent e) {
        Player p = e.getPlayer();
        PlayerHand hand = e.getHand();
        ItemStack item = e.getItemStack();
        Resolution r = resolve(p, hand, item);
        if (r == null || !r.resolved.enabled()) return;
        r.resolved.behavior().onFinish(r.ctx);
        if (p.getGameMode() == GameMode.CREATIVE) return;
        // Consume one: the client already predicted the decrement, so changing the stack confirms it (Minestom refreshes
        // the slot only when the item is unchanged). Remainder (potion -> bottle, stew -> bowl) = the type's explicit one,
        // else the item's USE_REMAINDER: replaces the stack on the last item, else added alongside the decremented stack (vanilla).
        Material remMat = r.ctx.consumable().remainder();
        ItemStack remainder = remMat != null ? ItemStack.of(remMat) : item.get(DataComponents.USE_REMAINDER);
        int left = item.amount() - 1;
        if (remainder != null && !remainder.isAir()) {
            if (left <= 0) {
                p.setItemInHand(hand, remainder);
            } else {
                p.setItemInHand(hand, item.withAmount(left));
                p.getInventory().addItemStack(remainder);
            }
        } else {
            p.setItemInHand(hand, left <= 0 ? ItemStack.AIR : item.withAmount(left));
        }
    }

    private void onCancel(PlayerCancelItemUseEvent e) {
        Resolution r = resolve(e.getPlayer(), e.getHand(), e.getItemStack());
        if (r == null || !r.resolved.enabled()) return;
        r.resolved.behavior().onCancel(r.ctx);
    }

    /** Per-instance "during" pass: each consuming player whose held item is an enabled registered consumable gets {@code onUsing}. */
    private void tick(Instance instance) {
        for (Player p : instance.getPlayers()) {
            if (!p.isUsingItem()) continue;
            PlayerHand hand = p.getItemUseHand();
            if (hand == null) continue;
            Resolution r = resolve(p, hand, p.getItemInHand(hand));
            if (r == null || !r.resolved.enabled()) continue;
            int remaining = r.resolved.consumeTicks() - (int) p.getCurrentItemUseTime();
            r.resolved.behavior().onUsing(r.ctx, Math.max(0, remaining));
        }
    }

    /** Installs reading the GLOBAL profile's {@link ConsumableConfig}: its {@code types} (the consumable identities) register up front. Set the profile before installing. */
    public static ConsumableSystem install(MinestomMechanics mm) {
        ConsumableConfig global = mm.profiles().resolve(null, MechanicsKeys.CONSUMABLES);
        return install(mm, global != null ? global : ConsumableConfig.builder().build());
    }

    /** Installs from an explicit config (the modular path): registers its {@code types}, installs the node, and hooks the shared tick loop once. */
    public static ConsumableSystem install(MinestomMechanics mm, ConsumableConfig cfg) {
        ConsumableSystem system = new ConsumableSystem(mm, cfg);
        mm.register(system);
        for (Consumable c : cfg.types()) system.register(c);
        mm.install(system.node);
        // Registered once for the JVM (TickSystem has no removal); dispatches through the live registry so a re-install is picked up.
        if (TICK_HOOK.compareAndSet(false, true)) {
            TickSystem.register(TickPhase.DEFAULT, ctx -> {
                ConsumableSystem live = mm.module(ConsumableSystem.class);
                if (live != null) live.tick(ctx.instance());
            });
        }
        return system;
    }
}
