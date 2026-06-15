package io.github.term4.minestommechanics.mechanics.projectile.types;

import net.kyori.adventure.key.Key;
import net.minestom.server.entity.EntityType;
import net.minestom.server.item.Material;

/**
 * Egg throwable: launched on a {@link Material#EGG} use. Config-free identity, like the snowball - the generic
 * {@link io.github.term4.minestommechanics.mechanics.projectile.entities.ManagedProjectile} handles the hit (0 damage
 * through the invul gate, knockback, break on any entity or block hit) and the throw + consume wiring is inherited from
 * {@link ThrowableItemType}. The vanilla {@code 1/8} baby-chicken-on-impact easter egg is NOT built in - it is a
 * one-liner custom {@link io.github.term4.minestommechanics.mechanics.projectile.ProjectileBehavior} a server can
 * attach via the egg's {@code behavior} config knob (see {@code ExampleServer} for the example).
 */
public final class Egg extends ThrowableItemType {

    public static final Key KEY = Key.key("minecraft:egg");
    public static final Egg INSTANCE = new Egg();

    private Egg() {
        super(KEY, "Egg", EntityType.EGG, Material.EGG);
    }
}
