package io.github.term4.minestommechanics.mechanics.damage.types.fire;

import io.github.term4.minestommechanics.mechanics.damage.types.DamageType;
import io.github.term4.minestommechanics.mechanics.damage.types.VanillaTypes;
import net.kyori.adventure.key.Key;

/**
 * Fire tick damage ({@code minecraft:fire}). Config-only for now; the self-driven fire-tick loop will
 * override {@link #enable}/{@link #disable} later, reading {@link FireDamageConfig} from the active config.
 */
public final class FireDamage extends DamageType {

    public static final Key KEY = Key.key("minecraft:fire");
    public static final FireDamage INSTANCE = new FireDamage();

    private FireDamage() {
        super(KEY, "Fire", VanillaTypes.FIRE,
                FireDamageConfig.builder().baseAmount(1.0).build());
    }
}
