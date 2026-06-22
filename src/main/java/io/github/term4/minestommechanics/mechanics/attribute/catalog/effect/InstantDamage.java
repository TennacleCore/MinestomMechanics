package io.github.term4.minestommechanics.mechanics.attribute.catalog.effect;

import io.github.term4.minestommechanics.mechanics.attribute.source.Behavior;
import io.github.term4.minestommechanics.mechanics.attribute.source.EntitySource;
import io.github.term4.minestommechanics.mechanics.attribute.source.Source;
import net.kyori.adventure.key.Key;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.damage.DamageType;

/**
 * Instant Damage (potion) - deals {@code 6 << amplifier} magic damage on apply (1.8 + 26 identical; vanilla
 * {@code HealOrHarmMobEffect}). The mirror of {@link InstantHealth}: like its direct heal, a direct entity op
 * (Minestom-native MAGIC damage, which bypasses armor) rather than a routed pipeline hit. The splash proximity-scaling /
 * undead inversion belong to the potion-throw path (not built yet).
 */
public final class InstantDamage {

    public static final Key KEY = Key.key("minecraft:instant_damage");

    private InstantDamage() {}

    private static final Behavior HARM = new Behavior() {
        @Override public void onApply(Entity entity, int level) {
            if (entity instanceof LivingEntity living) living.damage(DamageType.MAGIC, 6 << (level - 1));
        }
    };

    public static final Source INSTANCE = new EntitySource(KEY) {
        @Override public Behavior behavior() { return HARM; }
    };
}
