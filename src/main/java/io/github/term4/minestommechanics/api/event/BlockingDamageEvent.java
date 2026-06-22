package io.github.term4.minestommechanics.api.event;

import net.minestom.server.entity.Entity;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.damage.DamageType;
import net.minestom.server.event.trait.CancellableEvent;
import net.minestom.server.registry.RegistryKey;
import org.jetbrains.annotations.Nullable;

/**
 * Fired when a player blocks an incoming hit (sword block / shield), after the reduction is computed but before armor
 * mitigation runs. Listen to show feedback (an action-bar "Blocked! X -&gt; Y", a block sound). The amounts are
 * pre-mitigation (the value handed to armor is {@link #blockedAmount()}).
 *
 * <p><b>Cancellable:</b> cancelling vetoes the block - the hit takes its full {@link #originalAmount()} (e.g. a weapon
 * that pierces blocks).
 */
public final class BlockingDamageEvent implements CancellableEvent {

    private final Player defender;
    private final @Nullable Entity attacker;
    private final float originalAmount;
    private final float blockedAmount;
    private final RegistryKey<DamageType> damageType;
    private boolean cancelled;

    public BlockingDamageEvent(Player defender, @Nullable Entity attacker, float originalAmount, float blockedAmount,
                               RegistryKey<DamageType> damageType) {
        this.defender = defender;
        this.attacker = attacker;
        this.originalAmount = originalAmount;
        this.blockedAmount = blockedAmount;
        this.damageType = damageType;
    }

    /** The blocking player. */
    public Player defender() { return defender; }
    /** The entity dealing the hit, or {@code null}. */
    public @Nullable Entity attacker() { return attacker; }
    /** The incoming damage before the block. */
    public float originalAmount() { return originalAmount; }
    /** The damage after the block (less than {@link #originalAmount()}); the value the block passes on to armor. */
    public float blockedAmount() { return blockedAmount; }
    /** The Minestom damage type of the hit. */
    public RegistryKey<DamageType> damageType() { return damageType; }

    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancel) { this.cancelled = cancel; }
}
