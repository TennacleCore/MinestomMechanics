package io.github.term4.minestommechanics.mechanics.attribute.source;

import io.github.term4.minestommechanics.Services;
import net.minestom.server.entity.Entity;

/**
 * Generic lifecycle hooks a {@link Source} runs while active on an entity (apply / remove / tick). Domain-agnostic -
 * domain dispatch (e.g. combat on-hit) layers on top in the owning system. Every hook is a no-op by default.
 */
public interface Behavior {

    /** A behavior that does nothing (the default when a source has none). */
    Behavior NONE = new Behavior() {};

    default void onApply(Entity entity, int level) {}

    default void onRemove(Entity entity, int level) {}

    /** How often (ticks) {@link #onTick} fires while active at {@code level}; {@code 0} = never (the default). */
    default int tickInterval(int level) { return 0; }

    /** Periodic hook (every {@link #tickInterval}) while the source is active on {@code entity} at {@code level}. */
    default void onTick(Entity entity, int level) {}

    /** {@link #onTick} with the service hub, for behaviors that consume other systems (e.g. Hunger's exhaustion). */
    default void onTick(Services services, Entity entity, int level) { onTick(entity, level); }
}
