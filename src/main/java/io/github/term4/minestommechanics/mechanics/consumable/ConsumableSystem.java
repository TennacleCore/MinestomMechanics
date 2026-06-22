package io.github.term4.minestommechanics.mechanics.consumable;

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
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.item.PlayerCancelItemUseEvent;
import net.minestom.server.event.item.PlayerFinishItemUseEvent;
import net.minestom.server.event.player.PlayerUseItemEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Consumable items (food / drink) eaten over time. Mirrors the other type-registry systems (projectiles / damage):
 * registers on {@code mm}, owns an {@code EventNode}, resolves per-scope config through
 * {@code MechanicsProfiles.consumablesFor}, and holds a {@link ConsumableRegistry}. The use lifecycle maps onto
 * Minestom's seams - {@link PlayerUseItemEvent} (start) -&gt; {@link PlayerFinishItemUseEvent} (completed) /
 * {@link PlayerCancelItemUseEvent} (released early) - plus a per-tick "during" pass off {@link TickSystem}. Each phase
 * resolves the effective {@link ConsumableTypeConfig} via {@link ConsumableConfigResolver}, so the behavior/duration are
 * the scope's (a preset supplies the version-specific effects). Minestom fires the finish but neither applies effects
 * nor consumes the item, so that payload is ours.
 *
 * <p><b>Protocol fix.</b> Minestom derives the use duration from the modern {@code consumable} item component, which a
 * 1.8/Via client never carries - so the duration comes back {@code 0} and the finish never fires. We instead set it from
 * the resolved {@link ResolvedConsumable#consumeTicks()} in the start handler ({@link PlayerUseItemEvent#setItemUseTime}),
 * making completion server-authoritative and independent of the client version / item components.
 */
public final class ConsumableSystem {

    private final MinestomMechanics mm;
    private final Services services;
    private final ConsumableConfig config; // install config (the resolution fallback)
    private final EventNode<@NotNull Event> node;
    private final ConsumableRegistry registry = new ConsumableRegistry();
    /** The "during" tick is installed once for the JVM ({@link TickSystem} has no removal); it reads the live system each tick. */
    private static final AtomicBoolean TICK_HOOK = new AtomicBoolean();

    public ConsumableSystem(MinestomMechanics mm, ConsumableConfig config) {
        this.mm = mm;
        this.services = mm.services();
        this.config = config;
        this.node = EventNode.all("mm:consumable");
        node.addListener(PlayerUseItemEvent.class, this::onUse);
        node.addListener(PlayerFinishItemUseEvent.class, this::onFinish);
        node.addListener(PlayerCancelItemUseEvent.class, this::onCancel);
    }

    /** This system's listener node ({@code mm:consumable}); everything the system hooks lives under it. */
    public EventNode<@NotNull Event> node() { return node; }
    public ConsumableConfig config() { return config; }
    public ConsumableRegistry registry() { return registry; }

    /** Registers a consumable type (built-in or custom). */
    public ConsumableSystem register(Consumable consumable) { registry.register(consumable); return this; }

    /** Effective consumable config for {@code subject}: the scoped profile (player -&gt; instance -&gt; global), else the install config. */
    public ConsumableConfig configFor(@Nullable Entity subject) {
        ConsumableConfig scoped = mm.profiles().consumablesFor(subject);
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
        // the slot back only when the item is unchanged). The remainder (potion -> glass bottle, stew -> bowl) is the
        // type's explicit one, else the item's USE_REMAINDER component; it replaces the stack on the last one, else is
        // added alongside the decremented stack - vanilla behavior. No remainder + last one -> the slot clears to AIR.
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

    /** Installs the system active (a per-scope {@code MechanicsProfile.consumables} config can disable it); {@code types} register up front. Nothing is consumed until a {@link Consumable} is registered. */
    public static ConsumableSystem install(MinestomMechanics mm, Consumable... types) {
        return install(mm, ConsumableConfig.builder().build(), types);
    }

    /** Installs the consumable system: registers on {@code mm}, registers {@code types}, installs the node, and hooks the shared tick loop once. */
    public static ConsumableSystem install(MinestomMechanics mm, ConsumableConfig cfg, Consumable... types) {
        ConsumableSystem system = new ConsumableSystem(mm, cfg);
        mm.registerConsumables(system);
        for (Consumable c : types) system.register(c);
        mm.install(system.node);
        // Registered once for the JVM (TickSystem has no removal); dispatches through the live registry so a re-install is picked up.
        if (TICK_HOOK.compareAndSet(false, true)) {
            TickSystem.register(TickPhase.DEFAULT, ctx -> {
                ConsumableSystem live = mm.consumableSystem();
                if (live != null) live.tick(ctx.instance());
            });
        }
        return system;
    }
}
