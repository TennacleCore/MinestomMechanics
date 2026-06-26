package io.github.term4.minestommechanics.mechanics.vanilla18;

import io.github.term4.minestommechanics.MechanicsProfile;

/**
 * Vanilla 1.8 preset - the composed, pure-mechanics {@link MechanicsProfile}. The individual 1.8 configs live in the
 * {@code mechanics.vanilla18} subpackage ({@link Attack}, {@link Damage}, {@link Knockback}, {@link Projectiles},
 * {@link Attributes}, {@link Consumables}, {@link Blocking}, {@link Movement}, {@link Player}, and {@code Items}), which are also the
 * canonical defaults the systems fall back to. Assign with {@code mm.profiles().setGlobal(Vanilla18.profile())}.
 *
 * <p>Carries mechanics only - no compat or fixes (those install separately). Includes the item registry ({@link Items}).
 */
public final class Vanilla18 {

    private Vanilla18() {}

    /** The vanilla 1.8 mechanics profile (attack, damage, knockback, movement, projectiles, attributes, consumables, blocking). */
    public static MechanicsProfile profile() {
        return MechanicsProfile.builder()
                .attack(Attack.config())
                .damage(Damage.config())
                .knockback(Knockback.melee())
                .player(Player.config())
                .velocity(Movement.velocity())
                .projectiles(Projectiles.config())
                .attributes(Attributes.config())
                .consumables(Consumables.config())
                .blocking(Blocking.config())
                .items(Items.registry())
                .build();
    }
}
