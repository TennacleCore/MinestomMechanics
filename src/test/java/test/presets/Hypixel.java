package test.presets;

import io.github.term4.minestommechanics.Vanilla18;
import io.github.term4.minestommechanics.mechanics.damage.DamageConfig;
import io.github.term4.minestommechanics.mechanics.knockback.KnockbackConfig;
import io.github.term4.minestommechanics.tracking.ArcSpec;
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
                        .verticalStyle(VelocityRule.ArcStyle.CLOSED)
                        .launchOffset(VelocityRule.HYPIXEL_LAUNCH_OFFSET)
                        .build()))
                //  .sprintBuffer(buffer)
                .extraVertical(0.07)
                .build();
    }
}
