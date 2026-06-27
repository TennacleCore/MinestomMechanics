package io.github.term4.minestommechanics.api.event;

import io.github.term4.minestommechanics.Services;
import net.minestom.server.event.Event;
import org.jetbrains.annotations.Nullable;

/**
 * Shared base for the action-domain events (attack / damage / knockback): an immutable {@link #snapshot()} plus a
 * mutable {@link #finalSnap()} for processing, and the active {@link #services()}. Config previews and computed results
 * live on the subclasses; cancellable phases add cancellation via {@link CancellableMechanicsEvent}.
 *
 * @param <S> the domain snapshot type (e.g. {@code DamageSnapshot})
 */
public abstract class MechanicsEvent<S> implements Event {

    private final S snapshot;
    private final Services services;
    private @Nullable S finalSnap;

    protected MechanicsEvent(S snapshot, Services services) {
        this.snapshot = snapshot;
        this.services = services;
    }

    /** The original, immutable snapshot for this event. */
    public S snapshot() { return snapshot; }

    /** The snapshot used for processing - the {@link #finalSnap(Object) override} when set, else the original. */
    public S finalSnap() { return finalSnap != null ? finalSnap : snapshot; }
    public void finalSnap(S snap) { this.finalSnap = snap; }

    /** The active services (system lookups, scoped profiles). */
    public Services services() { return services; }
}
