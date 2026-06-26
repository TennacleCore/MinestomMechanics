package test.presets.hypixel;

import io.github.term4.minestommechanics.MechanicsKeys;
import io.github.term4.minestommechanics.MechanicsProfile;
import io.github.term4.minestommechanics.mechanics.vanilla18.Vanilla18;

/**
 * <b>Hypixel</b> example preset - the vanilla 1.8 baseline ({@link Vanilla18}) with Hypixel's velocity/damage/knockback
 * deltas (in this package). Projectiles are left unset on the profile until measured (do not assert the 1.8 ones).
 */
public final class Hypixel {

    private Hypixel() {}

    /** The Hypixel mechanics profile: the 1.8 baseline with Hypixel's velocity/damage/knockback deltas; projectiles unset. */
    public static MechanicsProfile profile() {
        return Vanilla18.profile().toBuilder()
                .set(MechanicsKeys.DAMAGE, Damage.config())
                .set(MechanicsKeys.KNOCKBACK, Knockback.melee())
                .set(MechanicsKeys.VELOCITY, Movement.velocity())
                .set(MechanicsKeys.PROJECTILES, null) // placeholder: Hypixel projectiles TBD from testing - do NOT inherit vanilla18's
                .build();
    }
}
