package io.github.term4.minestommechanics.mechanics.vanilla;

import io.github.term4.minestommechanics.MechanicsKeys;
import io.github.term4.minestommechanics.MechanicsProfile;

/**
 * Modern (26.1+) preset - the (incomplete) composed, pure-mechanics {@link MechanicsProfile}, built from the config
 * classes in this package ({@link Damage}, {@link Explosion}, {@link Projectiles}, {@link Attributes}, {@link Consumables},
 * {@link Movement}, and {@code Items}). Damage / explosion / projectiles / attributes / consumables / velocity are
 * implemented; attack, knockback, player, and blocking are still TODO. Assign with {@code mm.profiles().setGlobal(Vanilla.profile())}.
 *
 * <p>Carries mechanics only - no compat or fixes (those install separately). Includes the item registry ({@link Items}).
 */
public final class Vanilla {

    private Vanilla() {}

    /** The (incomplete) modern 26.1 mechanics profile. */
    public static MechanicsProfile profile() {
        return MechanicsProfile.builder()
                .set(MechanicsKeys.DAMAGE, Damage.config())
                .set(MechanicsKeys.DEATH, Death.config())
                .set(MechanicsKeys.PROJECTILES, Projectiles.config())
                .set(MechanicsKeys.ATTRIBUTES, Attributes.config())
                .set(MechanicsKeys.CONSUMABLES, Consumables.config())
                .set(MechanicsKeys.VELOCITY, Movement.velocity())
                .set(MechanicsKeys.ITEM_PHYSICS, io.github.term4.minestommechanics.vri.DroppedItemEntity.Model.MODERN)
                .set(MechanicsKeys.EXPLOSION, Explosion.config())
                .set(MechanicsKeys.ITEMS, Items.registry())
                .build();
    }
}
