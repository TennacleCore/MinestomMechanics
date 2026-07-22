package io.github.term4.minestommechanics.presets.vanilla;

import io.github.term4.minestommechanics.MechanicsKeys;
import io.github.term4.minestommechanics.MechanicsProfile;
import io.github.term4.minestommechanics.effect.Effects;
import io.github.term4.minestommechanics.vri.DroppedItemEntity;

/**
 * Modern (26.1+) preset - the composed, pure-mechanics {@link MechanicsProfile} built from the config classes in this
 * package. Mechanics only - no compat or fixes (those install separately). Incomplete: attack, knockback, player, and
 * blocking are still TODO. Assign with {@code mm.profiles().setGlobal(Vanilla.profile())}.
 */
public final class Vanilla {

    private Vanilla() {}

    public static MechanicsProfile profile() {
        return MechanicsProfile.builder()
                .set(MechanicsKeys.DAMAGE, Damage.config())
                .set(MechanicsKeys.DEATH, Death.config())
                .set(MechanicsKeys.PROJECTILES, Projectiles.config())
                .set(MechanicsKeys.ATTRIBUTES, Attributes.config())
                .set(MechanicsKeys.CONSUMABLES, Consumables.config())
                .set(MechanicsKeys.VELOCITY, Movement.velocity())
                .set(MechanicsKeys.ITEM_PHYSICS, DroppedItemEntity.Model.MODERN)
                .set(MechanicsKeys.EXPLOSION, Explosion.config())
                .set(MechanicsKeys.HUNGER, Hunger.config())
                .set(MechanicsKeys.ITEMS, Items.registry())
                .set(MechanicsKeys.EFFECTS, Effects.modern())
                .build();
    }
}
