package io.github.term4.minestommechanics.mechanics.attribute.catalog.enchant;

import io.github.term4.minestommechanics.mechanics.damage.types.melee.MeleeDamage;
import io.github.term4.minestommechanics.testsupport.HeadlessServerTest;
import net.minestom.server.component.DataComponents;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.item.component.EnchantmentList;
import net.minestom.server.item.enchant.Enchantment;
import net.minestom.server.registry.RegistryKey;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Fire Aspect's on-hit dispatch: a melee hit with a Fire Aspect weapon ignites the victim for {@code level × 4} seconds
 * (vanilla {@code EntityHuman.attack}). Exercises the damage system's post-hit weapon dispatch into the attribute catalog.
 */
class FireAspectTest extends HeadlessServerTest {

    @Test
    void ignitesVictimOnMeleeHit() {
        LivingEntity attacker = zombie(new Pos(0, 64, 500));
        LivingEntity victim = zombie(new Pos(0, 64, 501));
        victim.setHealth(20f);
        services.damage().apply(MeleeDamage.INSTANCE.snapshot(attacker, victim, false, fireAspectSword(2), services));
        assertEquals(2 * 4 * 20, victim.getFireTicks()); // level 2 -> 8s -> 160 ticks
    }

    @Test
    void plainWeaponDoesNotIgnite() {
        LivingEntity attacker = zombie(new Pos(0, 64, 502));
        LivingEntity victim = zombie(new Pos(0, 64, 503));
        victim.setHealth(20f);
        services.damage().apply(MeleeDamage.INSTANCE.snapshot(
                attacker, victim, false, ItemStack.of(Material.DIAMOND_SWORD), services));
        assertEquals(0, victim.getFireTicks());
    }

    private static ItemStack fireAspectSword(int level) {
        return ItemStack.of(Material.DIAMOND_SWORD).with(DataComponents.ENCHANTMENTS,
                new EnchantmentList(RegistryKey.<Enchantment>unsafeOf(FireAspect.KEY), level));
    }
}
