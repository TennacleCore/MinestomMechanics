package io.github.term4.minestommechanics.mechanics.attribute.catalog.effect;

import io.github.term4.minestommechanics.mechanics.attribute.source.Behavior;
import io.github.term4.minestommechanics.mechanics.attribute.source.EntitySource;
import io.github.term4.minestommechanics.mechanics.attribute.source.Source;
import net.kyori.adventure.key.Key;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.attribute.Attribute;

/**
 * Instant Health (potion) - heals {@code 4 << amplifier} on apply, capped at max health (1.8 + 26 identical). The splash
 * proximity-scaling / undead inversion belong to the potion-throw path (not built yet).
 */
public final class InstantHealth {

    public static final Key KEY = Key.key("minecraft:instant_health");

    private InstantHealth() {}

    private static final Behavior HEAL = new Behavior() {
        @Override public void onApply(Entity entity, int level) {
            if (!(entity instanceof LivingEntity living)) return;
            float max = (float) living.getAttributeValue(Attribute.MAX_HEALTH);
            float heal = 4 << (level - 1); // 4 << amplifier
            living.setHealth(Math.min(living.getHealth() + heal, max));
        }
    };

    public static final Source INSTANCE = new EntitySource(KEY) {
        @Override public Behavior behavior() { return HEAL; }
    };
}
