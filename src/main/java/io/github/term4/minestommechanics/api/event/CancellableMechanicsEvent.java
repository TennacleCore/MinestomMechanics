package io.github.term4.minestommechanics.api.event;

import io.github.term4.minestommechanics.Services;
import net.minestom.server.event.trait.CancellableEvent;

/**
 * A {@link MechanicsEvent} that can be cancelled (the {@code Pre} gate and the main modify phase). Cancelling drops the
 * pending action - what that means is documented per event (e.g. no damage, no knockback).
 *
 * @param <S> the domain snapshot type
 */
public abstract class CancellableMechanicsEvent<S> extends MechanicsEvent<S> implements CancellableEvent {

    private boolean cancelled;

    protected CancellableMechanicsEvent(S snapshot, Services services) {
        super(snapshot, services);
    }

    /** Cancels the action. */
    public void cancel() { setCancelled(true); }

    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancel) { this.cancelled = cancel; }
}
