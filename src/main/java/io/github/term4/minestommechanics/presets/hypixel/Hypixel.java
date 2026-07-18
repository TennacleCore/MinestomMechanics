package io.github.term4.minestommechanics.presets.hypixel;

import io.github.term4.minestommechanics.MechanicsKeys;
import io.github.term4.minestommechanics.MechanicsProfile;
import io.github.term4.minestommechanics.effect.Effects;
import io.github.term4.minestommechanics.mechanics.vanilla18.Vanilla18;

/**
 * <b>Hypixel</b> preset - the vanilla 1.8 baseline ({@link Vanilla18}) with Hypixel's velocity/damage/knockback/explosion
 * deltas and the measured BedWars fireball (in this package).
 *
 * <p>Carries mechanics only - cross-version compat and fixes install separately.
 */
public final class Hypixel {

    private Hypixel() {}

    /** The Hypixel mechanics profile: the 1.8 baseline with Hypixel's velocity/damage/knockback/explosion deltas and the measured fireball. */
    public static MechanicsProfile profile() {
        return Vanilla18.profile().toBuilder()
                .set(MechanicsKeys.DAMAGE, Damage.config())
                .set(MechanicsKeys.KNOCKBACK, Knockback.melee())
                .set(MechanicsKeys.VELOCITY, Movement.velocity())
                .set(MechanicsKeys.EXPLOSION, Explosion.config())
                .set(MechanicsKeys.PROJECTILES, Projectiles.config())
                // arrow hit-marker ding to the shooter (mmc18/hypixel/scrims18 enable it; vanilla presets don't)
                .set(MechanicsKeys.EFFECTS, Effects.vanilla18().register(Effects.ARROW_HIT_PLAYER, Effects.arrowHitMarker()))
                .build();
    }
}
