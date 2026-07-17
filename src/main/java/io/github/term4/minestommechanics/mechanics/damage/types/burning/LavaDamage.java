package io.github.term4.minestommechanics.mechanics.damage.types.burning;

import io.github.term4.minestommechanics.MinestomMechanics;
import io.github.term4.minestommechanics.mechanics.damage.DamageSystem;
import io.github.term4.minestommechanics.mechanics.attribute.defense.ProtectionCategory;
import io.github.term4.minestommechanics.mechanics.damage.types.DamageType;
import io.github.term4.minestommechanics.mechanics.damage.types.VanillaTypes;
import net.kyori.adventure.key.Key;

import java.util.Set;

/**
 * Lava damage ({@code minecraft:lava}). Vanilla 1.8: 4.0 damage attempted every tick while overlapping lava (the invul
 * window gates the cadence), igniting for 300 fire ticks unless wet. Self-driven via {@link BurningTicker}; tunables
 * come from {@link BurningConfig}.
 */
public final class LavaDamage extends DamageType {

    public static final Key KEY = Key.key("minecraft:lava");
    public static final LavaDamage INSTANCE = new LavaDamage();

    private LavaDamage() {
        super(KEY, "Lava", VanillaTypes.LAVA, BurningConfig.builder().key(KEY).build());
    }

    @Override public Set<ProtectionCategory> protectionCategories() { return Set.of(ProtectionCategory.FIRE); }

    @Override
    public void enable(DamageSystem system, MinestomMechanics mm) {
        BurningTicker.activate(KEY, system);
    }

    @Override
    public void disable() {
        BurningTicker.deactivate(KEY);
    }
}
