package test.presets;

import io.github.term4.minestommechanics.Vanilla18;
import io.github.term4.minestommechanics.mechanics.damage.DamageConfig;
import io.github.term4.minestommechanics.mechanics.knockback.KnockbackConfig;
import io.github.term4.minestommechanics.tracking.ArcSpec;
import io.github.term4.minestommechanics.tracking.Physics;
import io.github.term4.minestommechanics.tracking.VelocityRule;

public final class Hypixel {

    private Hypixel() {}

    /** Returns DamageConfig based on Hypixel. */
    public static DamageConfig dmg() {
        return DamageConfig.builder(Vanilla18.dmg())
                .overdamageSilent(true)
                .invulTicks(9)
                .build();
    }

    public static KnockbackConfig kb() {
        //  int buffer = 1; I think this is unnecessary but leaving here just in case

        return KnockbackConfig.builder(Vanilla18.kb())
                .velocity(VelocityRule.simulated(ArcSpec.builder()
                        .verticalStyle(VelocityRule.ArcStyle.PER_TICK)
                        .launchOffset(VelocityRule.HYPIXEL_LAUNCH_OFFSET)
                        // Hypixel's vertical KB is a per-tick gravity curve that does NOT apply vanilla's
                        // motY < 0.005 near-zero clamp (the apex "reseed"). With the clamp on, the apex's
                        // residual ~+0.003 b/t is zeroed, so the descending arc folds ~0.003 b/t too low
                        // (~11 wire-shorts down by the third hit: 859 vs the 870 it should be). clampY(0)
                        // disables the apex reseed for this arc only; clampX/clampZ stay vanilla so the
                        // horizontal PER_TICK arc is unchanged, and Vanilla18/Minemen keep their 0.005 reseed.
                        .physics(Physics.vanilla().toBuilder().clampY(0).build())
                        .build()))
                //  .sprintBuffer(buffer)
                .extraVertical(0.07)
                .build();
    }
}
