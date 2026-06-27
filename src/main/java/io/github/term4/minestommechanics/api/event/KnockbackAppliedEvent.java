package io.github.term4.minestommechanics.api.event;

import io.github.term4.minestommechanics.Services;
import io.github.term4.minestommechanics.mechanics.knockback.KnockbackSnapshot;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.event.Event;
import org.jetbrains.annotations.Nullable;

/**
 * Fired after a knockback's velocity has been applied to the target - informational. Carries the {@link #velocity()}
 * actually set. The post-knockback counterpart to {@link PreKnockbackEvent} / {@link KnockbackEvent}.
 */
public final class KnockbackAppliedEvent implements Event {

    private final KnockbackSnapshot snapshot;
    private final Services services;
    private final Vec velocity;

    public KnockbackAppliedEvent(KnockbackSnapshot snapshot, Vec velocity, Services services) {
        this.snapshot = snapshot;
        this.services = services;
        this.velocity = velocity;
    }

    /** The snapshot the knockback was applied from. */
    public KnockbackSnapshot snapshot() { return snapshot; }

    /** The active services (system lookups, scoped profiles). */
    public Services services() { return services; }

    /** Velocity actually applied (post-quantize when on). */
    public Vec velocity() { return velocity; }

    public @Nullable Entity source() { return snapshot.source(); }
    public @Nullable Entity target() { return snapshot.target(); }
}
