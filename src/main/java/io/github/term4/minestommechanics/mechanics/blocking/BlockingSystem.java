package io.github.term4.minestommechanics.mechanics.blocking;

import io.github.term4.minestommechanics.MinestomMechanics;
import io.github.term4.minestommechanics.Services;
import io.github.term4.minestommechanics.api.event.BlockingDamageEvent;
import io.github.term4.minestommechanics.api.event.BlockingStartEvent;
import io.github.term4.minestommechanics.api.event.BlockingStopEvent;
import io.github.term4.minestommechanics.mechanics.blocking.BlockingConfigResolver.BlockingContext;
import io.github.term4.minestommechanics.mechanics.blocking.BlockingConfigResolver.ResolvedBlocking;
import io.github.term4.minestommechanics.mechanics.damage.DamageConfigResolver.DamageContext;
import io.github.term4.minestommechanics.mechanics.damage.DamageSystem;
import net.minestom.server.component.DataComponents;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.PlayerHand;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventDispatcher;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.item.PlayerCancelItemUseEvent;
import net.minestom.server.event.item.PlayerFinishItemUseEvent;
import net.minestom.server.event.player.PlayerUseItemEvent;
import net.minestom.server.item.ItemStack;
import net.minestom.server.tag.Tag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Item blocking (sword block / shield) - a thin damage hook over the scope {@link BlockingConfig}. Blocking is driven by
 * the item's {@code blocks_attacks} component: the client predicts the block from it (every version), Minestom turns it
 * into a use time, and the server is "blocking" while {@link Player#isUsingItem()}. So an item blocks when it (a) carries
 * the component, (b) has a material the active config marks blockable, and (c) isn't opted out via {@link #BLOCKABLE}.
 * The reduction is applied by {@link DamageSystem#apply} via {@link #reduce} (before armor, vanilla order); <em>how</em> a
 * hit is reduced is the resolved {@link BlockingBehavior}'s call.
 *
 * <p>Blocking is <b>not</b> forced for a plain (component-less) blockable material - a modern client can't block a plain
 * sword, so forcing it server-side would desync. Give a sword the component to make it block
 * ({@code VanillaBlocking.withBlocking} / {@code item}); the config still gates which materials may block + how. Movement
 * slowdown is client-predicted, so nothing is applied server-side.
 */
public final class BlockingSystem {

    /**
     * Per-item opt-out: absent = blockable, {@code false} = opted out. A configured-blockable material blocks unless its
     * stack sets this {@code false}; an opted-out item never enters the block state and never affects damage.
     */
    public static final Tag<Boolean> BLOCKABLE = Tag.Boolean("mm:blockable");

    private final MinestomMechanics mm;
    private final Services services;
    private final BlockingConfig config; // install config (the resolution fallback)
    private final EventNode<@NotNull Event> node;

    public BlockingSystem(MinestomMechanics mm, BlockingConfig config) {
        this.mm = mm;
        this.services = mm.services();
        this.config = config;
        this.node = EventNode.all("mm:blocking");
        node.addListener(PlayerUseItemEvent.class, this::onUse);
        node.addListener(PlayerCancelItemUseEvent.class, e -> onStopUsing(e.getPlayer(), e.getHand(), e.getItemStack()));
        node.addListener(PlayerFinishItemUseEvent.class, e -> onStopUsing(e.getPlayer(), e.getHand(), e.getItemStack()));
    }

    private void onUse(PlayerUseItemEvent e) {
        // Only blocks_attacks items reach a block use-state (the client predicts it); bow/food/etc. aren't ours.
        ItemStack item = e.getItemStack();
        if (!item.has(DataComponents.BLOCKS_ATTACKS)) return;
        Player player = e.getPlayer();
        if (blockable(item, configFor(player))) {
            BlockingStartEvent start = new BlockingStartEvent(player, e.getHand(), item);
            EventDispatcher.call(start);
            if (start.isCancelled()) e.setItemUseTime(0); // a listener denied the block
        } else {
            e.setItemUseTime(0); // a component item we won't block (opted out / not configured) - no phantom block
        }
    }

    /** Fires {@link BlockingStopEvent} when a player ends a block use (released, finished, or interrupted). */
    private void onStopUsing(Player player, PlayerHand hand, ItemStack item) {
        if (item.has(DataComponents.BLOCKS_ATTACKS) && blockable(item, configFor(player))) {
            EventDispatcher.call(new BlockingStopEvent(player, hand, item));
        }
    }

    /** Whether {@code item} blocks under {@code cfg}: not opted out via {@link #BLOCKABLE}, and its material is configured blockable. */
    private boolean blockable(ItemStack item, @Nullable BlockingConfig cfg) {
        return !Boolean.FALSE.equals(item.getTag(BLOCKABLE)) && cfg != null && cfg.blocks(item.material());
    }

    /** This system's listener node ({@code mm:blocking}). */
    public EventNode<@NotNull Event> node() { return node; }
    public BlockingConfig config() { return config; }

    /** Effective blocking config for {@code subject} (the defender): the scoped profile, else the install config. */
    public BlockingConfig configFor(@Nullable Entity subject) {
        BlockingConfig scoped = mm.profiles().blockingFor(subject);
        return scoped != null ? scoped : config;
    }

    /** Whether {@code player} is currently raising a blockable item (use-state only; the per-hit gates live in {@link #reduce}). */
    public boolean isBlocking(Player player) {
        if (!player.isUsingItem()) return false;
        PlayerHand hand = player.getItemUseHand();
        return hand != null && blockable(player.getItemInHand(hand), configFor(player));
    }

    /**
     * Reduces a blocked hit, returning the (possibly reduced) amount. Returns {@code amount} unchanged when the victim
     * isn't blocking (or the item/material is opted out / not blockable in the scope) or the behavior declines; otherwise
     * fires {@link BlockingDamageEvent} and returns the reduced amount. A block never increases damage.
     */
    public float reduce(LivingEntity victim, DamageContext damage, float amount) {
        if (amount <= 0 || !(victim instanceof Player p) || !p.isUsingItem()) return amount;
        PlayerHand hand = p.getItemUseHand();
        if (hand == null) return amount;
        ItemStack item = p.getItemInHand(hand);
        BlockingConfig cfg = configFor(p);
        if (!blockable(item, cfg)) return amount;

        BlockingContext ctx = new BlockingContext(p, item, hand, damage, services);
        ResolvedBlocking r = BlockingConfigResolver.resolve(cfg, ctx);
        if (!r.enabled()) return amount;

        float reduced = r.behavior().apply(ctx, r, amount);
        if (reduced >= amount) return amount; // a block never increases damage
        BlockingDamageEvent ev = new BlockingDamageEvent(p, damage.snap().source(), amount, reduced,
                damage.snap().type().minecraftType());
        EventDispatcher.call(ev);
        if (ev.isCancelled()) return amount; // a listener vetoed the block (e.g. a piercing weapon)
        return reduced;
    }

    /** Installs the system with an empty config (nothing blocks until a scope/install config marks materials blockable). */
    public static BlockingSystem install(MinestomMechanics mm) {
        return install(mm, BlockingConfig.builder().build());
    }

    /** Installs the blocking system: registers on {@code mm} and installs the node. */
    public static BlockingSystem install(MinestomMechanics mm, BlockingConfig cfg) {
        BlockingSystem system = new BlockingSystem(mm, cfg);
        mm.registerBlocking(system);
        mm.install(system.node);
        return system;
    }
}
