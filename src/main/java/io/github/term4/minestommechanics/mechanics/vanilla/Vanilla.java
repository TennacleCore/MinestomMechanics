package io.github.term4.minestommechanics.mechanics.vanilla;

import io.github.term4.minestommechanics.MechanicsProfile;

/**
 * Modern (26.1+) preset - the (incomplete) composed, pure-mechanics {@link MechanicsProfile}, built from the config
 * classes in this package ({@link Damage}, {@link Projectiles}, {@link Attributes}, {@link Consumables}, {@link Movement},
 * and {@code Items}). Damage / projectiles / attributes / consumables / velocity are implemented; attack, knockback,
 * player, and blocking are still TODO. Assign with {@code mm.profiles().setGlobal(Vanilla.profile())}.
 *
 * <p>Carries mechanics only - no compat or fixes (those install separately). Includes the item registry ({@link Items}).
 */
public final class Vanilla {

    private Vanilla() {}

    /** The (incomplete) modern 26.1 mechanics profile (damage, projectiles, attributes, consumables, velocity). */
    public static MechanicsProfile profile() {
        return MechanicsProfile.builder()
                .damage(Damage.config())
                .projectiles(Projectiles.config())
                .attributes(Attributes.config())
                .consumables(Consumables.config())
                .velocity(Movement.velocity())
                .items(Items.registry())
                .build();
    }
}
