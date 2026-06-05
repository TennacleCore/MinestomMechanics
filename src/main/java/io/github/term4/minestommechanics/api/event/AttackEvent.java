package io.github.term4.minestommechanics.api.event;

import io.github.term4.minestommechanics.Services;
import io.github.term4.minestommechanics.mechanics.attack.AttackConfigResolver;
import io.github.term4.minestommechanics.mechanics.attack.AttackSystem;
import io.github.term4.minestommechanics.mechanics.Cause;
import io.github.term4.minestommechanics.mechanics.attack.AttackSnapshot;
import io.github.term4.minestommechanics.tracking.GroundTracker;
import io.github.term4.minestommechanics.tracking.SprintTracker;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.Player;
import net.minestom.server.event.Event;
import net.minestom.server.item.ItemStack;
import org.jetbrains.annotations.Nullable;

// This is the public facing API users can hook into to get information or change how an attack event happens
/** Fired when a hit is detected. */ // Future plans to allow mobs / other entities to fire this event, or for users to manually fire it.
public final class AttackEvent implements Event {

    private final AttackSnapshot snapshot;
    private AttackSnapshot finalSnap;

    private boolean process = true; // process this attack, true by default (probs update from a boolean for which processor to use)
    private @Nullable AttackRule.Ruleset ruleset; // override for which attack rule to use for this attack

    private Boolean overrideSprint;
    private Boolean overrideCritical;
    private ItemStack overrideItem;

    private final boolean invulnerable;
    private boolean bypassInvul;

    private boolean cancelled;

    private final Services services;
    private final AttackConfigResolver.ResolvedAttackConfig resolvedConfig;

    public AttackEvent(AttackSnapshot snapshot, Services services) {
        this.snapshot = snapshot;
        this.services = services;
        this.invulnerable = snapshot.target() != null && AttackSystem.isInvulnerableToAttack(snapshot.target());
        this.resolvedConfig = snapshot.config() != null
                ? AttackConfigResolver.resolve(snapshot.config(), AttackConfigResolver.AttackContext.of(snapshot, services))
                : AttackConfigResolver.ResolvedAttackConfig.defaults();
    }

    // Setters
    public void finalSnap(AttackSnapshot snap) { this.finalSnap = snap; }
    public void process(boolean process) { this.process = process; }
    public void processor(AttackRule.Ruleset ruleset) { this.ruleset = ruleset; }
    public void overrideSprint(@Nullable Boolean b) { this.overrideSprint = b; }
    public void overrideCritical(@Nullable Boolean b) { this.overrideCritical = b; }
    public void overrideItem(@Nullable ItemStack item) { this.overrideItem = item; }
    public void bypassInvul(boolean b) { this.bypassInvul = b; }
    public void cancel() { this.cancelled = true; }

    // Getters
    public AttackSnapshot snapshot() { return snapshot; }
    public AttackSnapshot finalSnap() { return finalSnap != null ? finalSnap : snapshot; }
    public Entity attacker() { return finalSnap().attacker(); }
    public @Nullable Entity target() { return finalSnap().target(); }
    public Cause cause() { return finalSnap().cause(); }
    public boolean process() { return process; }
    public @Nullable AttackRule.Ruleset processor() { return ruleset; }
    public boolean invulnerable() { return invulnerable; }
    public boolean bypassInvul() { return bypassInvul; }
    public @Nullable Boolean overrideSprint() { return overrideSprint; }
    public @Nullable Boolean overrideCritical() { return overrideCritical; }
    public @Nullable ItemStack overrideItem() { return overrideItem; }
    public boolean sprint() {
        if (overrideSprint != null) return overrideSprint;
        if (!(attacker() instanceof Player p) || services.sprintTracker() == null) { return attacker().isSprinting(); }
        int buffer = resolvedConfig.sprintBuffer() != null ? resolvedConfig.sprintBuffer() : 0;
        return SprintTracker.wasRecentlySprinting(services.sprintTracker(), p, buffer);
    }

    /** Attacker is off the ground and descending (a melee crit precondition). */
    public boolean attackerFalling() {
        return GroundTracker.isFalling(attacker());
    }

    /** Whether the attacker is a flying player (creative/spectator flight or granted flight). */
    public boolean attackerFlying() {
        return attacker() instanceof Player p && p.isFlying();
    }

    /**
     * Whether this attack is a critical hit, as decided by the configured {@link CriticalRule}.
     * An explicit {@link #overrideCritical(Boolean)} takes precedence over the rule.
     */
    public boolean critical() {
        if (overrideCritical != null) return overrideCritical;
        CriticalRule rule = resolvedConfig.criticalRule();
        return rule != null && rule.isCritical(this);
    }
    public @Nullable ItemStack item() {
        if (overrideItem != null) return overrideItem;
        return attacker() instanceof LivingEntity le ? le.getItemInMainHand() : ItemStack.AIR;
    }

    public boolean cancelled() { return cancelled; }

    /** Resolved attack config for this event. */
    public AttackConfigResolver.ResolvedAttackConfig resolvedConfig() { return resolvedConfig; }

    /**
     * Defines what counts as a critical hit for an attack. Supplied via {@code AttackConfig.criticalRule(...)}
     * and evaluated per attack; the rule only <em>decides</em> whether a hit is critical, while the melee
     * damage type applies the <em>effect</em> (its crit multiplier) when building the damage snapshot.
     *
     * <p>A rule receives the full {@link AttackEvent}, so it can branch on anything (attacker, item,
     * target, airborne/sprint state, etc.). It must NOT call {@link AttackEvent#critical()} - that would
     * recurse infinitely.
     */
    @FunctionalInterface
    public interface CriticalRule {

        /** Rule used when an attack config does not specify one. */
        CriticalRule DEFAULT = vanilla();

        /** Whether the given attack is a critical hit. */
        boolean isCritical(AttackEvent event);

        /** Vanilla rule: attacker is falling, or is flying (flight always crits). */
        static CriticalRule vanilla() { return e -> e.attackerFalling() || e.attackerFlying(); } // TODO: After adding attributes, blindness prevents crits

        /** Never critical. */
        static CriticalRule never() { return e -> false; }

        /** Always critical. */
        static CriticalRule always() { return e -> true; }
    }

    /**
     * Processes an attack (or attempted attack): applies the configured combat ruleset (damage,
     * knockback, cooldown, etc.) for a detected hit. Selected via {@code AttackConfig.ruleset(...)} as
     * a {@link Ruleset} factory so a fresh rule instance can be created per attack with the active services.
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
