package io.github.term4.minestommechanics.mechanics.damage;

import net.minestom.server.event.entity.EntityTickEvent;

// TODO: consolidate every ticker into one lib-wide ticker (we have a tick clock); needs ordering review
/** Per-tick environmental damage scan registered on {@link io.github.term4.minestommechanics.tracking.EnvironmentalDamageTicker}. */
@FunctionalInterface
public interface EnvironmentalTickProducer {

    void tick(EntityTickEvent event, DamageSystem system);
}
