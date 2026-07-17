package io.github.term4.minestommechanics.mechanics.blocking;

import io.github.term4.minestommechanics.MechanicsKeys;
import io.github.term4.minestommechanics.MechanicsModule;
import io.github.term4.minestommechanics.MinestomMechanics;
import io.github.term4.minestommechanics.Services;
import io.github.term4.minestommechanics.api.event.blocking.BlockingDamageEvent;
import io.github.term4.minestommechanics.api.event.blocking.BlockingStartEvent;
import io.github.term4.minestommechanics.api.event.blocking.BlockingStopEvent;
import io.github.term4.minestommechanics.mechanics.blocking.BlockingConfigResolver.BlockingContext;
import io.github.term4.minestommechanics.mechanics.blocking.BlockingConfigResolver.ResolvedBlocking;
import io.github.term4.minestommechanics.mechanics.damage.DamageConfigResolver.DamageContext;
import io.github.term4.minestommechanics.mechanics.damage.DamageSystem;
import net.minestom.server.component.DataComponents;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.PlayerHand;
import net.minestom.server.event.EventDispatcher;
import net.minestom.server.event.EventFilter;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.item.PlayerCancelItemUseEvent;
import net.minestom.server.event.item.PlayerFinishItemUseEvent;
import net.minestom.server.event.player.PlayerUseItemEvent;
import net.minestom.server.event.trait.PlayerEvent;
import net.minestom.server.item.ItemStack;
import net.minestom.server.tag.Tag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Item blocking (sword block / shield) - a thin damage hook over the scope {@link BlockingConfig}. Driven by the item's
 * {@code blocks_attacks} component: the client predicts the block (every version), Minestom turns it into a use time, and
 * the server is "blocking" while {@link Player#isUsingItem()}. So an item blocks when it (a) carries the component,
 * (b) has a config-blockable material, and (c) isn't opted out via {@link #BLOCKABLE}. The reduction is applied before
 * armor (vanilla order) by {@link DamageSystem#apply} via {@link #reduce}; <em>how</em> is the resolved {@link BlockingBehavior}'s call.
 *
 * <p>Blocking is <b>not</b> forced for a plain (component-less) blockable material - a modern client can't block a plain
 * sword, so forcing it server-side would desync. Add the component to make a sword block ({@code VanillaBlocking.withBlocking});
 * the config still gates which materials block + how. Movement slowdown is client-predicted, nothing applied server-side.
 */
public final class BlockingSystem implements MechanicsModule {

    /** Per-item opt-out: absent = blockable, {@code false} = opted out (never enters the block state, never affects damage). */
    public static final Tag<Boolean> BLOCKABLE = Tag.Boolean("mm:blockable");

    private final MinestomMechanics mm;
    private final Services services;
    private final BlockingConfig config;
    private final EventNode<@NotNull PlayerEvent> node;

    public BlockingSystem(MinestomMechanics mm, BlockingConfig config) {
        this.mm = mm;
        this.services = mm.services();
        this.config = config;
        this.node = EventNode.type("mm:blocking", EventFilter.PLAYER);
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

    public EventNode<@NotNull PlayerEvent> node() { return node; }
    public BlockingConfig config() { return config; }

    /** Effective config for {@code subject} (the defender): the scoped profile, else the install config. */
    public BlockingConfig configFor(@Nullable Entity subject) {
        BlockingConfig scoped = mm.profiles().resolve(subject, MechanicsKeys.BLOCKING);
        return scoped != null ? scoped : config;
    }

    /** Whether {@code player} is currently raising a blockable item (use-state only; the per-hit gates live in {@link #reduce}). */
    public boolean isBlocking(Player player) {
        if (!player.isUsingItem()) return false;
        PlayerHand hand = player.getItemUseHand();
        return hand != null && blockable(player.getItemInHand(hand), configFor(player));
    }

    /**
     * Reduces a blocked hit. Returns {@code amount} unchanged if the victim isn't blocking, the item isn't blockable in
     * scope, or the behavior declines; else fires {@link BlockingDamageEvent} and returns the reduced amount (never increases it).
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

    public static BlockingSystem install(MinestomMechanics mm, BlockingConfig cfg) {
        BlockingSystem system = new BlockingSystem(mm, cfg);
        mm.register(system);
        mm.install(system.node);
        return system;
    }
}
