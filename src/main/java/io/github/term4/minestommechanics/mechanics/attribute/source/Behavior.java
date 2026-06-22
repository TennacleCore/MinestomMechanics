package io.github.term4.minestommechanics.mechanics.attribute.source;

import net.minestom.server.entity.Entity;

/**
 * Generic lifecycle hooks a {@link Source} runs while active on an entity - the {@code ProjectileBehavior} idiom. Kept
 * version-agnostic and domain-agnostic (apply / remove / tick); domain dispatch (e.g. combat on-hit) layers on top of
 * these in the system that owns those events. Every hook is a no-op by default.
 */
public interface Behavior {

    /** A behavior that does nothing (the default when a source has none). */
    Behavior NONE = new Behavior() {};

    /** The source became active on {@code entity} at {@code level}. */
    default void onApply(Entity entity, int level) {}

    /** The source (at {@code level}) stopped being active on {@code entity}. */
    default void onRemove(Entity entity, int level) {}

    /** How often (ticks) {@link #onTick} fires while active at {@code level}; {@code 0} = never (the default). */
    default int tickInterval(int level) { return 0; }

    /** Periodic hook (every {@link #tickInterval}) while the source is active on {@code entity} at {@code level}. */
    default void onTick(Entity entity, int level) {}
}
