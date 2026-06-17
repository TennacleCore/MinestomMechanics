package io.github.term4.minestommechanics.mechanics.damage.types.burning;

import io.github.term4.minestommechanics.MinestomMechanics;
import io.github.term4.minestommechanics.mechanics.damage.DamageSystem;
import io.github.term4.minestommechanics.mechanics.damage.types.DamageType;
import io.github.term4.minestommechanics.mechanics.damage.types.VanillaTypes;
import net.kyori.adventure.key.Key;

/**
 * On-fire (burn tick) damage ({@code minecraft:on_fire}). Vanilla 1.8: 1.0 damage every 20 fire ticks while burning.
 * Rides Minestom's per-entity fire ticks, so anything that ignites an entity feeds it. Self-driven via the shared
 * {@link BurningTicker}; tunables come from {@link BurningConfig}.
 */
public final class BurningDamage extends DamageType {

    public static final Key KEY = Key.key("minecraft:on_fire");
    public static final BurningDamage INSTANCE = new BurningDamage();

    private BurningDamage() {
        super(KEY, "Burning", VanillaTypes.ON_FIRE, BurningConfig.builder().key(KEY).build());
    }

    @Override
    public void enable(DamageSystem system, MinestomMechanics mm) {
        BurningTicker.activate(KEY, system, mm);
    }

    @Override
    public void disable() {
        BurningTicker.deactivate(KEY);
    }
}
