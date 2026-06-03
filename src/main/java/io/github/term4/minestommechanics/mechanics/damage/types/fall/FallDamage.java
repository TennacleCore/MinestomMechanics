package io.github.term4.minestommechanics.mechanics.damage.types.fall;

import io.github.term4.minestommechanics.mechanics.damage.types.DamageType;
import io.github.term4.minestommechanics.mechanics.damage.types.DamageTypeConfig;
import io.github.term4.minestommechanics.mechanics.damage.types.VanillaTypes;
import net.kyori.adventure.key.Key;

/**
 * Fall damage ({@code minecraft:fall}). Config-only for now; self-driven fall-distance tracking will
 * override {@link #enable}/{@link #disable} later.
 */
public final class FallDamage extends DamageType {

    public static final Key KEY = Key.key("minecraft:fall");
    public static final FallDamage INSTANCE = new FallDamage();

    private FallDamage() {
        super(KEY, "Fall", VanillaTypes.FALL,
                DamageTypeConfig.builder(KEY).baseAmount(0.0).build());
    }
}
