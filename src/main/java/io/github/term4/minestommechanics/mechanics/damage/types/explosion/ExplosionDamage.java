package io.github.term4.minestommechanics.mechanics.damage.types.explosion;

import io.github.term4.minestommechanics.mechanics.attribute.defense.ProtectionCategory;
import io.github.term4.minestommechanics.mechanics.damage.types.DamageType;
import io.github.term4.minestommechanics.mechanics.damage.types.DamageTypeConfig;
import io.github.term4.minestommechanics.mechanics.damage.types.VanillaTypes;
import net.kyori.adventure.key.Key;

import java.util.Set;

/**
 * Explosion damage ({@code minecraft:explosion}): the {@code ExplosionSystem} computes the per-entity amount from the
 * exposure/falloff curve and hands it in via the snapshot's amount override, so this type only declares the Blast
 * Protection category + Minestom mapping. Knockback and the exposure raytrace stay in the system.
 */
public final class ExplosionDamage extends DamageType {

    public static final Key KEY = Key.key("minecraft:explosion");
    public static final ExplosionDamage INSTANCE = new ExplosionDamage();

    private ExplosionDamage() {
        // ownsVelocityBroadcast: the ExplosionSystem delivers the full knockback (base KB + push) itself, so the
        // DamageSystem must not also send its hurt-velocity broadcast.
        super(KEY, "Explosion", VanillaTypes.EXPLOSION,
                DamageTypeConfig.builder(KEY).baseAmount(0.0).ownsVelocityBroadcast(true).build());
    }

    @Override public Set<ProtectionCategory> protectionCategories() { return Set.of(ProtectionCategory.EXPLOSION); }
}
