package test.presets.mmc18;

import io.github.term4.minestommechanics.MechanicsKeys;
import io.github.term4.minestommechanics.MechanicsProfile;
import io.github.term4.minestommechanics.mechanics.vanilla18.Vanilla18;

/**
 * <b>MMC 1.8</b> preset - replicates MineMenClub's 1.8 PvP feel, built on the {@link Vanilla18} 1.8 baseline with the MMC
 * deltas in this package ({@link Attack} hit-queue, {@link Damage} silent overdamage, {@link Knockback} components,
 * {@link Explosion} two-impulse KB (melee hurt-KB + scaled push), {@link Projectiles} fireball). Velocity tracking is inherited from {@link Vanilla18}.
 *
 * <p>Carries mechanics only - cross-version compat ({@code Compat18}) and fixes install separately.
 */
public final class Mmc18 {

    private Mmc18() {}

    /** The mmc18 mechanics profile: the vanilla 1.8 baseline with the MMC combat / movement / explosion / projectile deltas. */
    public static MechanicsProfile profile() {
        return Vanilla18.profile().toBuilder()
                .set(MechanicsKeys.ATTACK, Attack.config())
                .set(MechanicsKeys.DAMAGE, Damage.config())
                .set(MechanicsKeys.KNOCKBACK, Knockback.melee())
                .set(MechanicsKeys.PLAYER, Player.config())
                // VELOCITY inherited from Vanilla18 (plain simulated): the vertical hold masks fluid, horizontal is unused
                .set(MechanicsKeys.EXPLOSION, Explosion.fireballFight())
                .set(MechanicsKeys.PROJECTILES, Projectiles.config())
                .build();
    }
}
