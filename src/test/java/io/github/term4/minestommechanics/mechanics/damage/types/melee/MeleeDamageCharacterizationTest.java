package io.github.term4.minestommechanics.mechanics.damage.types.melee;

import io.github.term4.minestommechanics.mechanics.damage.DamageSnapshot;
import io.github.term4.minestommechanics.testsupport.HeadlessServerTest;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Golden/characterization pins for {@link MeleeDamage#snapshot} - the final baked melee amount = 1.8 weapon damage ×
 * (crit ? 1.5 : 1) - including item resolution and the main-hand fallback. The raw weapon values are pinned by
 * {@code ItemRegistryParityTest}; this pins the snapshot integration (crit multiplier + the configured
 * {@code player_attack} path through the installed {@code DamageSystem}). The weapon damage feeds {@code attackDamage}
 * (channel A, now via the ItemRegistry) and crit is an offense stage - the products below must hold. See docs/attributes-design.md.
 */
class MeleeDamageCharacterizationTest extends HeadlessServerTest {

    private static final float EPS = 1e-6f;

    private static LivingEntity attacker;
    private static LivingEntity target;

    private static LivingEntity attacker() {
        if (attacker == null) attacker = zombie(new Pos(0, 64, 20));
        return attacker;
    }

    private static LivingEntity target() {
        if (target == null) target = zombie(new Pos(0, 64, 21));
        return target;
    }

    private float amount(boolean crit, ItemStack item) {
        DamageSnapshot snap = MeleeDamage.INSTANCE.snapshot(attacker(), target(), crit, item, services);
        return snap.amount();
    }

    @Test
    void weaponNoCrit() {
        assertEquals(7.0f, amount(false, ItemStack.of(Material.DIAMOND_SWORD)), EPS);
    }

    @Test
    void weaponCritMultipliesBy1_5() {
        assertEquals(10.5f, amount(true, ItemStack.of(Material.DIAMOND_SWORD)), EPS); // 7 × 1.5
        assertEquals(6.0f, amount(true, ItemStack.of(Material.WOODEN_SWORD)), EPS);   // 4 × 1.5
    }

    @Test
    void fistWhenNoItem() {
        assertEquals(1.0f, amount(false, null), EPS);
    }

    @Test
    void fallsBackToAttackerMainHand() {
        LivingEntity atk = attacker();
        atk.setItemInMainHand(ItemStack.of(Material.IRON_AXE));
        try {
            // null item arg -> resolves the attacker's held weapon (iron axe = 5.0)
            DamageSnapshot snap = MeleeDamage.INSTANCE.snapshot(atk, target(), false, null, services);
            assertEquals(5.0f, snap.amount(), EPS);
        } finally {
            atk.setItemInMainHand(ItemStack.AIR);
        }
    }
}
