package test.presets.mmc18;

import io.github.term4.minestommechanics.MechanicsKeys;
import io.github.term4.minestommechanics.MechanicsProfile;
import io.github.term4.minestommechanics.effect.Effects;
import io.github.term4.minestommechanics.mechanics.vanilla18.Vanilla18;
import io.github.term4.minestommechanics.tracking.motion.VelocityConfig;
import io.github.term4.minestommechanics.tracking.motion.VelocityRule;

/**
 * <b>MMC 1.8</b> preset - replicates MineMenClub's 1.8 PvP feel, built on the {@link Vanilla18} 1.8 baseline with the MMC
 * deltas in this package ({@link Attack} hit-queue, {@link Damage} silent overdamage, {@link Knockback} components,
 * {@link Explosion} two-impulse KB (melee hurt-KB + scaled push), {@link Projectiles} fireball).
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
                // MineMen quirk (user-measured): a lag-frozen victim's motY holds until its next move packet
                .set(MechanicsKeys.VELOCITY, VelocityRule.simulated(
                        VelocityConfig.builder().motYOnMovePacket(true).build()))
                .set(MechanicsKeys.EXPLOSION, Explosion.fireballFight())
                .set(MechanicsKeys.PROJECTILES, Projectiles.config())
                // arrow hit-marker ding to the shooter (mmc18/hypixel/scrims18 enable it; vanilla presets don't)
                .set(MechanicsKeys.EFFECTS, Effects.vanilla18().register(Effects.ARROW_HIT_PLAYER, Effects.arrowHitMarker()))
                .build();
    }
}
