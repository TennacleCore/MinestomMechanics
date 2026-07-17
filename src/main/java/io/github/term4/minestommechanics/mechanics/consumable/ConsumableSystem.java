package io.github.term4.minestommechanics.mechanics.consumable;

import io.github.term4.minestommechanics.util.tick.TickContext;
import io.github.term4.minestommechanics.MechanicsKeys;
import io.github.term4.minestommechanics.MechanicsModule;
import io.github.term4.minestommechanics.MinestomMechanics;
import io.github.term4.minestommechanics.Services;
import io.github.term4.minestommechanics.api.event.consume.ConsumeAppliedEvent;
import io.github.term4.minestommechanics.api.event.consume.ConsumeEvent;
import io.github.term4.minestommechanics.api.event.consume.PreConsumeEvent;
import io.github.term4.minestommechanics.effect.EffectContext;
import io.github.term4.minestommechanics.effect.Effects;
import io.github.term4.minestommechanics.mechanics.consumable.ConsumableConfigResolver.ConsumableContext;
import io.github.term4.minestommechanics.mechanics.consumable.ConsumableConfigResolver.ResolvedConsumable;
import io.github.term4.minestommechanics.platform.fixes.FixesConfig;
import io.github.term4.minestommechanics.platform.fixes.client.LegacyConsumeFixConfig;
import io.github.term4.minestommechanics.util.tick.TickPhase;
import io.github.term4.minestommechanics.util.tick.TickSystem;
import net.kyori.adventure.key.Key;
import net.minestom.server.component.DataComponents;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityStatuses;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.PlayerHand;
import net.minestom.server.event.EventDispatcher;
import net.minestom.server.event.EventFilter;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.ListenerHandle;
import net.minestom.server.event.item.PlayerCancelItemUseEvent;
import net.minestom.server.event.item.PlayerFinishItemUseEvent;
import net.minestom.server.event.player.PlayerUseItemEvent;
import net.minestom.server.event.trait.PlayerEvent;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.network.packet.server.play.EntityStatusPacket;
import net.minestom.server.utils.inventory.PlayerInventoryUtils;
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
    private static final AtomicBoolean TICK_HOOK = new AtomicBoolean();
    // Pre/Applied fire only when listened to; the main ConsumeEvent always fires
    private static final ListenerHandle<PreConsumeEvent> PRE_CONSUME = EventDispatcher.getHandle(PreConsumeEvent.class);
    private static final ListenerHandle<ConsumeAppliedEvent> CONSUME_APPLIED = EventDispatcher.getHandle(ConsumeAppliedEvent.class);

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

    /** Resolves the effective values for {@code player} consuming {@code item} in {@code hand}, or {@code null} if it's neither a registered consumable nor a component food. */
    private @Nullable Resolution resolve(Player player, PlayerHand hand, ItemStack item) {
        ConsumableConfig cfg = configFor(player);
        Consumable c = registry.forMaterial(item.material());
        if (c == null) {
            // the ComponentFood floor: an unregistered item with a food component eats with its registry values
            if (Boolean.FALSE.equals(cfg.componentFoods()) || item.get(DataComponents.FOOD) == null) return null;
            c = ComponentFood.TYPE;
        }
        ConsumableContext ctx = new ConsumableContext(player, item, hand, c, services);
        return new Resolution(ctx, ConsumableConfigResolver.resolve(cfg, ctx));
    }

    private record Resolution(ConsumableContext ctx, ResolvedConsumable resolved) {}

    private void onUse(PlayerUseItemEvent e) {
        Player p = e.getPlayer();
        Resolution r = resolve(p, e.getHand(), e.getItemStack());
        if (r == null || !r.resolved.enabled()) return;
        if (PRE_CONSUME.hasListener()) {
            PreConsumeEvent pre = new PreConsumeEvent(r.ctx, services);
            EventDispatcher.call(pre);
            if (pre.isCancelled()) {
                e.setItemUseTime(0);
                return;
            }
            if (pre.finalSnap() != r.ctx) r = new Resolution(pre.finalSnap(), ConsumableConfigResolver.resolve(configFor(p), pre.finalSnap()));
        }
        // canConsume gate (1.8 creative can't eat food): zero the item's NATIVE consumable duration, else Minestom
        // starts the use anyway and viewers see the hand-active bit as an eating animation
        if (!r.resolved.canConsume()) {
            e.setItemUseTime(0);
            return;
        }
        // Legacy re-use gate: a 1.8 client (unlike 1.13.2+, which waits for the server to confirm the last consume
        // ended) spam-restarts a use under lag and double-eats. Refuse a fresh consume while already mid-use; the hand
        // frees on release / finish / slot-switch, so switching items is a fresh use.
        if (legacyConsumeEnabled(p) && p.getItemUseHand() != null) {
            e.setItemUseTime(0);
            return;
        }
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
        ConsumeEvent consume = new ConsumeEvent(r.ctx, services);
        EventDispatcher.call(consume);
        if (consume.isCancelled()) {
            p.getInventory().update(p); // the client already predicted the consume; repaint it
            return;
        }
        (consume.behavior() != null ? consume.behavior() : r.resolved.behavior()).onFinish(consume.finalSnap());
        // food burps on finish (vanilla ItemFood.onFoodEaten); drinks don't. The chew sound + eating particles are
        // client-driven off the hand-active metadata, so the burp is the only server-sent eating effect.
        if (item.get(DataComponents.FOOD) != null) Effects.play(services, Effects.BURP, EffectContext.of(p));
        if (p.getGameMode() != GameMode.CREATIVE) {
            // Remainder = the type's explicit one (potion -> bottle, stew -> bowl), else USE_REMAINDER.
            Material remMat = r.ctx.consumable().remainder();
            ItemStack remainder = remMat != null ? ItemStack.of(remMat) : item.get(DataComponents.USE_REMAINDER);
            int left = item.amount() - 1;
            boolean legacy = legacyConsumeEnabled(p);
            if (remainder != null && !remainder.isAir() && left <= 0) {
                p.setItemInHand(hand, remainder); // last unit -> bottle/bowl: a genuine item change (echoed) either way
            } else {
                ItemStack held = left <= 0 ? ItemStack.AIR : item.withAmount(left);
                // Legacy decrements SILENTLY - a 1.8 client self-decrements from entity_status 9, and an echoed set_slot
                // would clear the eat before status 9 lands. A modern client predicts its own consume, so a plain echo matches.
                if (legacy) consumeHeld(p, hand, held);
                else p.setItemInHand(hand, held);
                if (remainder != null && !remainder.isAir()) p.getInventory().addItemStack(remainder); // extra bottle alongside: not predicted
            }
            // Legacy count pacing: status 9 (self) makes the client decrement + clear its use, then a window_items confirm
            // right behind it re-anchors the count. status-9-first is required so the confirm isn't cleared early; Minestom's
            // own status 9 (fired after this event) then no-ops. Full-inventory confirm on purpose - a targeted single-slot
            // re-anchor paces noticeably worse in-game despite touching the same slot.
            if (legacy) {
                p.sendPacket(new EntityStatusPacket(p.getEntityId(), (byte) EntityStatuses.Player.MARK_ITEM_FINISHED));
                p.getInventory().update(p);
            }
        }
        if (CONSUME_APPLIED.hasListener()) EventDispatcher.call(new ConsumeAppliedEvent(consume.finalSnap(), services));
    }

    /** Applies the eaten-item count to the held slot without a slot echo - the 1.8 client decrements it itself from {@code entity_status 9}. */
    private static void consumeHeld(Player p, PlayerHand hand, ItemStack item) {
        int slot = hand == PlayerHand.OFF ? PlayerInventoryUtils.OFFHAND_SLOT : p.getHeldSlot();
        p.getInventory().setItemStack(slot, item, false);
    }

    /** Whether the legacy consume fix ({@link FixesConfig#legacyConsume()}) applies to {@code p}: enabled in its scope AND a legacy client. */
    private boolean legacyConsumeEnabled(Player p) {
        FixesConfig fixes = mm.profiles().resolve(p, MechanicsKeys.FIXES);
        LegacyConsumeFixConfig c = fixes != null ? fixes.legacyConsume() : null;
        return c != null && Boolean.TRUE.equals(c.enabled()) && mm.clientInfo().isLegacy(p);
    }

    private void onCancel(PlayerCancelItemUseEvent e) {
        Resolution r = resolve(e.getPlayer(), e.getHand(), e.getItemStack());
        if (r == null || !r.resolved.enabled()) return;
        r.resolved.behavior().onCancel(r.ctx);
    }

    /** Per-instance "during" pass: each consuming player whose held item is an enabled registered consumable gets {@code onUsing}. */
    private void tick(TickContext ctx) {
        for (Player p : ctx.world().players()) {
            if (!ctx.owns(p) || !p.isUsingItem()) continue;
            PlayerHand hand = p.getItemUseHand();
            if (hand == null) continue;
            Resolution r = resolve(p, hand, p.getItemInHand(hand));
            if (r == null || !r.resolved.enabled()) continue;
            int remaining = r.resolved.consumeTicks() - (int) p.getCurrentItemUseTime();
            r.resolved.behavior().onUsing(r.ctx, Math.max(0, remaining));
            emitConsumeSound(r, remaining);
        }
    }

    /**
     * The chew / drink sound on the vanilla cadence ({@code Consumable.shouldEmitParticlesAndSounds}: after 21.875% of
     * the use elapses, then every 4 ticks). Routed through the effect registry as {@link Effects#EAT} (food) or
     * {@link Effects#DRINK} - the chew sound Minestom never plays. Food vs drink follows the {@code FOOD} component, as the burp does.
     */
    private void emitConsumeSound(Resolution r, int remaining) {
        int consumeTicks = r.resolved.consumeTicks();
        if (remaining < 0 || remaining % 4 != 0 || consumeTicks - remaining <= (int) (consumeTicks * 0.21875f)) return;
        Key sound = r.ctx.item().get(DataComponents.FOOD) != null ? Effects.EAT : Effects.DRINK;
        Effects.play(services, sound, EffectContext.of(r.ctx.user()));
    }

    /** Installs reading the GLOBAL profile's {@link ConsumableConfig}: its {@code types} (the consumable identities) register up front. Set the profile before installing. */
    public static ConsumableSystem install(MinestomMechanics mm) {
        ConsumableConfig global = mm.profiles().resolve(null, MechanicsKeys.CONSUMABLES);
        return install(mm, global != null ? global : ConsumableConfig.builder().build());
    }

    /** Installs from an explicit config (the modular path): registers its {@code types}, installs the node, and hooks the shared tick loop once. */
    public static ConsumableSystem install(MinestomMechanics mm, ConsumableConfig cfg) {
        ConsumableSystem system = new ConsumableSystem(mm, cfg);
        mm.register(system); // detaches a replaced install's node
        for (Consumable c : cfg.types()) system.register(c);
        mm.install(system.node);
        // Registered once for the JVM (TickSystem has no removal); dispatches through the live registry so a re-install is picked up.
        if (TICK_HOOK.compareAndSet(false, true)) {
            TickSystem.register(TickPhase.DEFAULT, ctx -> {
                ConsumableSystem live = mm.module(ConsumableSystem.class);
                if (live != null) live.tick(ctx);
            });
        }
        return system;
    }
}
