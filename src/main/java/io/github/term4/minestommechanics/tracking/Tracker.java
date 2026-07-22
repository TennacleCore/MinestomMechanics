package io.github.term4.minestommechanics.tracking;

import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;

/**
 * A listener node that stamps per-player transient state. Enabled trackers mount under {@code mm:trackers} at
 * {@link io.github.term4.minestommechanics.MinestomMechanics#init() init()}.
 */
public interface Tracker {

    EventNode<? extends Event> node();

    /** One-time hook run at install, before {@link #node()} mounts. */
    default void start() {}
}
