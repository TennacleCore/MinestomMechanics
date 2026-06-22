package io.github.term4.minestommechanics.api.event;

import io.github.term4.minestommechanics.Services;
import io.github.term4.minestommechanics.mechanics.attack.AttackConfig;
import io.github.term4.minestommechanics.mechanics.attack.AttackConfigResolver;
import io.github.term4.minestommechanics.mechanics.attack.AttackSnapshot;
import io.github.term4.minestommechanics.tracking.motion.MotionTracker;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.Player;
import net.minestom.server.event.trait.CancellableEvent;
import net.minestom.server.item.ItemStack;
import net.minestom.server.potion.PotionEffect;
import org.jetbrains.annotations.Nullable;

/**
 * Public API fired when a hit is detected, before the attack ruleset runs. Wraps the {@link AttackSnapshot} like
 * {@link KnockbackEvent}/{@link DamageEvent}: an immutable {@link #snapshot()} plus a mutable {@link #finalSnap()},
 * with per-hit overrides for the ruleset, crit verdict, and item.
 */
// Future plans to allow mobs / other entities to fire this event, or for users to manually fire it.
public final class AttackEvent implements CancellableEvent {

    private final AttackSnapshot snapshot;
    private @Nullable AttackSnapshot finalSnap;

    private boolean process = true;
    private @Nullable AttackRule.Ruleset ruleset;

    private @Nullable Boolean overrideCritical;
    private @Nullable ItemStack overrideItem;

    private boolean cancelled;

    private final Services services;

    public AttackEvent(AttackSnapshot snapshot, Services services) {
        this.snapshot = snapshot;
        this.services = services;
    }

    /** Original attack data (immutable). */
    public AttackSnapshot snapshot() { return snapshot; }

    /**
     * Snapshot handed to attack processing.
     * Set via {@code event.finalSnap(event.snapshot().toBuilder().target(x).build())}.
     */
    public AttackSnapshot finalSnap() { return finalSnap != null ? finalSnap : snapshot; }
    public void finalSnap(AttackSnapshot snap) { this.finalSnap = snap; }

    /** Attack config used for this hit ({@code null} = defaults). */
    public @Nullable AttackConfig config() { return finalSnap().config(); }

    /** Replaces the config used for this hit (sugar for rebuilding {@link #finalSnap()}). */
    public void config(AttackConfig config) { finalSnap(finalSnap().withConfig(config)); }

    /**
     * The effective plain values for this hit: the {@link #config() current config} resolved against it (re-resolved
     * from the current {@link #finalSnap()} each call, so it reflects listener changes). {@code null} config = defaults.
     */
    public AttackConfigResolver.ResolvedAttackConfig resolvedConfig() {
        AttackSnapshot s = finalSnap();
        return s.config() != null
                ? AttackConfigResolver.resolve(s.config(), AttackConfigResolver.AttackContext.of(s, services))
                : AttackConfigResolver.ResolvedAttackConfig.defaults();
    }

    /** Whether the attack proceeds to the ruleset after the event ({@code false} = detected but not processed). */
    public boolean process() { return process; }
    public void process(boolean process) { this.process = process; }

    /** Per-hit ruleset override, or {@code null} to use the config's ruleset. */
    public @Nullable AttackRule.Ruleset processor() { return ruleset; }
    public void processor(AttackRule.Ruleset ruleset) { this.ruleset = ruleset; }

    /** Per-hit crit override ({@code null} = let the {@link CriticalRule} decide). */
    public @Nullable Boolean overrideCritical() { return overrideCritical; }
    public void overrideCritical(@Nullable Boolean b) { this.overrideCritical = b; }

    /** Per-hit item override ({@code null} = the attacker's main hand). */
    public @Nullable ItemStack overrideItem() { return overrideItem; }
    public void overrideItem(@Nullable ItemStack item) { this.overrideItem = item; }

    // delegating accessors
    public Entity attacker() { return finalSnap().attacker(); }
    public @Nullable Entity target() { return finalSnap().target(); }

    /** Attacker is off the ground and descending (a melee crit precondition). */
    public boolean attackerFalling() {
        return MotionTracker.isFalling(attacker());
    }

    /** Whether the attacker is a flying player (creative/spectator flight or granted flight). */
    public boolean attackerFlying() {
        return attacker() instanceof Player p && p.isFlying();
    }

    /** Whether the attacker is blinded - vanilla blindness suppresses crits ({@code 1.8/26: !hasEffect(BLINDNESS)}). */
    public boolean attackerBlind() {
        return attacker().getEffectLevel(PotionEffect.BLINDNESS) >= 0;
    }

    /**
     * Whether this attack is a critical hit, as decided by the configured {@link CriticalRule}.
     * An explicit {@link #overrideCritical(Boolean)} takes precedence over the rule.
     */
    public boolean critical() {
        if (overrideCritical != null) return overrideCritical;
        CriticalRule rule = resolvedConfig().criticalRule();
        return rule != null && rule.isCritical(this);
    }

    /** The attacking item: the {@link #overrideItem(ItemStack) override} when set, else the attacker's main hand. */
    public @Nullable ItemStack item() {
        if (overrideItem != null) return overrideItem;
        return attacker() instanceof LivingEntity le ? le.getItemInMainHand() : ItemStack.AIR;
    }

    /** Cancel the attack (no ruleset runs). */
    public void cancel() { setCancelled(true); }

    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancel) { this.cancelled = cancel; }

    /**
     * Defines what counts as a critical hit. Supplied via {@code AttackConfig.criticalRule(...)} and evaluated per
     * attack; it only decides whether a hit is critical (the melee type applies the multiplier). Receives the full
     * {@link AttackEvent}; must not call {@link AttackEvent#critical()} (infinite recursion).
     */
    @FunctionalInterface
    public interface CriticalRule {

        /** Rule used when an attack config does not specify one. */
        CriticalRule DEFAULT = vanilla();

        /** Whether the given attack is a critical hit. */
        boolean isCritical(AttackEvent event);

        /** Vanilla rule: attacker is falling (or flying), and not blinded (1.8/26 blindness suppresses crits). */
        static CriticalRule vanilla() { return e -> (e.attackerFalling() || e.attackerFlying()) && !e.attackerBlind(); }

        /** Never critical. */
        static CriticalRule never() { return e -> false; }

        /** Always critical. */
        static CriticalRule always() { return e -> true; }
    }

    /**
     * Processes a detected hit: applies the configured combat ruleset (damage, knockback, ...). Selected via
     * {@code AttackConfig.ruleset(...)} as a {@link Ruleset} factory so a fresh rule is created per attack.
     */
    @FunctionalInterface
    public interface AttackRule {

        /**
         * Called when the server receives an attack packet (or an emulated attack).
         *
         * @param event the finalized attack event for this hit (target is nullable for swing + raytraced emulated attacks)
         */
        void processAttack(AttackEvent event);

        /** Factory that creates an {@link AttackRule} bound to the active services. */
        @FunctionalInterface
        interface Ruleset {
            AttackRule create(Services services);
        }
    }

}
