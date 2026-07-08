package io.github.term4.minestommechanics.mechanics.knockback;

import io.github.term4.minestommechanics.mechanics.attribute.catalog.enchant.Knockback;
import io.github.term4.minestommechanics.testsupport.HeadlessServerTest;
import io.github.term4.minestommechanics.item.Enchants;
import io.github.term4.minestommechanics.mechanics.vanilla18.Vanilla18;
import net.minestom.server.component.DataComponents;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.item.component.EnchantmentList;
import net.minestom.server.item.enchant.Enchantment;
import net.minestom.server.registry.RegistryKey;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The unified extra-knockback level: the Knockback enchant (read off the weapon by the attack ruleset) and a projectile's
 * Punch both arrive as {@code KnockbackSnapshot.extraKnockback}, and a melee sprint adds {@code +1} - all scaling the
 * config's {@code extra}* knobs. So one explicit extra level reproduces a sprint hit's golden vector {@code (0, 10, 18)}.
 */
class KnockbackEnchantTest extends HeadlessServerTest {

    private Vec hit(LivingEntity source, LivingEntity target, int extraLevel) {
        return new KnockbackCalculator(services, Vanilla18.knockback())
                .compute(new KnockbackSnapshot(target, true, source, null, null, Vanilla18.knockback(), extraLevel));
    }

    @Test
    void oneExtraLevelMatchesASprintHit() {
        LivingEntity target = zombie(new Pos(40, 64, 0));
        LivingEntity source = zombie(new Pos(40, 64, -1, 0f, 0f));
        source.setSprinting(false);
        Vec kb = hit(source, target, 1); // a Knockback-I weapon == +1 extra level == a sprint hit
        assertEquals(0.0, kb.x(), 1e-9);
        assertEquals(10.0, kb.y(), 1e-9);
        assertEquals(18.0, kb.z(), 1e-9);
    }

    @Test
    void higherLevelKnocksFurther() {
        LivingEntity target = zombie(new Pos(42, 64, 0));
        LivingEntity source = zombie(new Pos(42, 64, -1, 0f, 0f));
        source.setSprinting(false);
        double z1 = hit(source, target, 1).z();
        double z2 = hit(source, target, 2).z();
        assertTrue(z2 > z1, "more extra levels reach further (" + z2 + " vs " + z1 + ")");
    }

    @Test
    void projectilePunchAppliesWithoutMelee() {
        // a projectile feeds its captured Punch as the extra level on a melee=false snapshot (no sprint bonus path); the
        // extra* knobs must still scale by it - guards ManagedProjectile.buildKnockback passing punchLevel() through.
        LivingEntity target = zombie(new Pos(44, 64, 0));
        LivingEntity source = zombie(new Pos(44, 64, -1, 0f, 0f));
        Vec none = new KnockbackCalculator(services, Vanilla18.knockback())
                .compute(new KnockbackSnapshot(target, false, source, null, null, Vanilla18.knockback(), 0));
        Vec punch = new KnockbackCalculator(services, Vanilla18.knockback())
                .compute(new KnockbackSnapshot(target, false, source, null, null, Vanilla18.knockback(), 1));
        assertTrue(punch.z() > none.z(), "Punch level 1 reaches further than no Punch (" + punch.z() + " vs " + none.z() + ")");
    }

    @Test
    void enchantsReadsKnockbackLevelOffTheWeapon() {
        // the read the attack ruleset performs on event.item() to feed extraKnockback
        ItemStack sword = ItemStack.of(Material.DIAMOND_SWORD)
                .with(DataComponents.ENCHANTMENTS, new EnchantmentList(RegistryKey.<Enchantment>unsafeOf(Knockback.KEY), 2));
        assertEquals(2, Enchants.level(sword, Knockback.KEY));
        assertEquals(0, Enchants.level(ItemStack.of(Material.DIAMOND_SWORD), Knockback.KEY));
    }
}
