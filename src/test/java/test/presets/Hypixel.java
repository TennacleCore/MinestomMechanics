package test.presets;

import io.github.term4.minestommechanics.Vanilla18;
import io.github.term4.minestommechanics.mechanics.damage.DamageConfig;
import io.github.term4.minestommechanics.mechanics.knockback.KnockbackConfig;
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
                .velocityModeH(VelocityRule.input())
                .velocityModeV(VelocityRule.gravityPredicted(0.08, 0.98))
                .velocityModeExtraH(VelocityRule.input())
                .velocityModeExtraV(VelocityRule.gravityPredicted(0.08, 0.98))
                //  .sprintBuffer(buffer)
                .extraVertical(0.07)
                .frictionH(1.0)
                .frictionExtraH(1.0)
                .build();
    }
}
