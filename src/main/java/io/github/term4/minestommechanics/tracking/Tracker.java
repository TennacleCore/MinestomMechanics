package io.github.term4.minestommechanics.tracking;

import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;

/**
 * A tracker: a listener node that stamps per-player transient state (sprint, motion, client info). Enabled
 * trackers are started and mounted under the {@code mm:trackers} node at
 * {@link io.github.term4.minestommechanics.MinestomMechanics#init() init()}.
 */
public interface Tracker {

    /** The tracker's listener node. */
    EventNode<? extends Event> node();

    /** One-time hook run at install, before {@link #node()} mounts (scheduler tasks etc.). */
    default void start() {}
}
