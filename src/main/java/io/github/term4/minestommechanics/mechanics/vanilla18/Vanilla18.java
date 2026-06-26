package io.github.term4.minestommechanics.mechanics.vanilla18;

import io.github.term4.minestommechanics.MechanicsKeys;
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
                .set(MechanicsKeys.ATTACK, Attack.config())
                .set(MechanicsKeys.DAMAGE, Damage.config())
                .set(MechanicsKeys.KNOCKBACK, Knockback.melee())
                .set(MechanicsKeys.PLAYER, Player.config())
                .set(MechanicsKeys.VELOCITY, Movement.velocity())
                .set(MechanicsKeys.PROJECTILES, Projectiles.config())
                .set(MechanicsKeys.ATTRIBUTES, Attributes.config())
                .set(MechanicsKeys.CONSUMABLES, Consumables.config())
                .set(MechanicsKeys.BLOCKING, Blocking.config())
                .set(MechanicsKeys.ITEMS, Items.registry())
                .build();
    }
}
