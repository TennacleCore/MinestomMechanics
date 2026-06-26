package test.presets.mmc18;

import io.github.term4.minestommechanics.MechanicsProfile;
import io.github.term4.minestommechanics.mechanics.projectile.ProjectileConfig;
import io.github.term4.minestommechanics.mechanics.vanilla18.Projectiles;
import io.github.term4.minestommechanics.mechanics.vanilla18.Vanilla18;

/**
 * <b>MMC 1.8</b> preset - replicates MineMenClub's 1.8 PvP feel, built on the {@link Vanilla18} 1.8 baseline with the
 * MMC deltas in this package ({@link Attack} hit-queue, {@link Damage} silent overdamage, {@link Knockback} components,
 * {@link Movement} velocity rule). The facade {@link #profile()} composes them.
 *
 * <p>Carries mechanics only - cross-version compat ({@code Compat18}) and fixes install separately. Projectiles are
 * deliberately left unset on the profile - mmc18's values are still a {@link #projectiles() placeholder} until measured,
 * so the profile does not claim the 1.8 ones.
 */
public final class Mmc18 {

    private Mmc18() {}

    /** The mmc18 mechanics profile: the vanilla 1.8 baseline with the MMC combat/movement deltas; projectiles left unset. */
    public static MechanicsProfile profile() {
        return Vanilla18.profile().toBuilder()
                .attack(Attack.config())
                .damage(Damage.config())
                .knockback(Knockback.melee())
                .player(Player.config())
                .velocity(Movement.velocity())
                .projectiles(null) // placeholder: mmc18 projectiles TBD from testing - do NOT inherit vanilla18's
                .build();
    }

    // TODO: replace with the measured MineMen projectile values; until then this reuses the 1.8 baseline so a server can
    // still install ProjectileSystem. NOT wired into profile() - the profile must not assert mmc18 uses 1.8 projectiles.
    /** Placeholder projectile config (the 1.8 baseline) for installing {@code ProjectileSystem} until mmc18's values exist. */
    public static ProjectileConfig projectiles() {
        return ProjectileConfig.builder(Projectiles.config()).build();
    }
}
