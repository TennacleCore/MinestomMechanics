package io.github.term4.minestommechanics.api.event;

import io.github.term4.minestommechanics.Services;
import io.github.term4.minestommechanics.mechanics.damage.DamageSnapshot;
import io.github.term4.minestommechanics.mechanics.damage.DamageSystem.DamageOutcome;
import io.github.term4.minestommechanics.mechanics.damage.types.DamageType;
import net.minestom.server.entity.Entity;
import net.minestom.server.event.Event;
import net.minestom.server.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Fired after a damage instance has been applied - informational (not cancellable). Carries the amount actually
 * {@link #dealt()} and the {@link #outcome()}, for stats, logging, and on-hit triggers that need the real result.
 * The post-damage counterpart to {@link PreDamageEvent} / {@link DamageEvent}.
 */
public final class DamageAppliedEvent implements Event {

    private final DamageSnapshot snapshot;
    private final Services services;
    private final float dealt;
    private final DamageOutcome outcome;

    public DamageAppliedEvent(DamageSnapshot snapshot, float dealt, DamageOutcome outcome, Services services) {
        this.snapshot = snapshot;
        this.services = services;
        this.dealt = dealt;
        this.outcome = outcome;
    }

    /** The snapshot the damage was applied from. */
    public DamageSnapshot snapshot() { return snapshot; }

    /** The active services (system lookups, scoped profiles). */
    public Services services() { return services; }

    /** The damage amount actually applied (after overdamage/mitigation); {@code 0} when nothing landed. */
    public float dealt() { return dealt; }

    /** How the hit resolved (fresh / overdamage / blocked / immune). */
    public DamageOutcome outcome() { return outcome; }

    // delegating accessors
    public DamageType type() { return snapshot.type(); }
    public Entity target() { return snapshot.target(); }
    public @Nullable Entity source() { return snapshot.source(); }
    /** Item involved in the damage (melee weapon, later a projectile's bow), or {@code null}. */
    public @Nullable ItemStack item() { return snapshot.item(); }
}
