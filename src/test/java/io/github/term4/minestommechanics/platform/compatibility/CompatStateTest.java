package io.github.term4.minestommechanics.platform.compatibility;

import io.github.term4.minestommechanics.testsupport.HeadlessServerTest;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.minestom.server.component.DataComponents;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.item.component.AttackRange;
import net.minestom.server.item.component.EnchantmentList;
import net.minestom.server.item.enchant.Enchantment;
import net.minestom.server.network.packet.server.play.SetSlotPacket;
import net.minestom.server.registry.RegistryKey;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The attack_range stamp + throwable reskin are client-view only; {@link CompatState#sanitizeInboundItem} keeps a creative echo from becoming server state. */
class CompatStateTest extends HeadlessServerTest {

    private static ItemStack stampedSword() {
        return ItemStack.of(Material.DIAMOND_SWORD).with(DataComponents.ATTACK_RANGE, new AttackRange(0f, 3f, 0f, 5f, 0.1f, 1f));
    }

    @Test
    void sanitizeStripsEchoedStampForStampedClient() {
        CompatState s = new CompatState();
        s.apply(Compat18.config()); // attackHitboxMargin 0.1 -> this client is stamped
        assertTrue(s.stampsAttackRange());
        assertNull(s.sanitizeInboundItem(stampedSword()).get(DataComponents.ATTACK_RANGE),
                "a stamped client's echoed attack_range is stripped");
    }

    @Test
    void sanitizeLeavesItemsAloneWhenNotStamping() {
        CompatState s = new CompatState(); // OFF policy -> not stamped
        assertFalse(s.stampsAttackRange());
        assertNotNull(s.sanitizeInboundItem(stampedSword()).get(DataComponents.ATTACK_RANGE),
                "a non-stamped client keeps a legit attack_range");
    }

    private static ItemStack slotItem(CompatState s, ItemStack item) {
        return ((SetSlotPacket) s.rewriteItems(new SetSlotPacket(0, 0, (short) 36, item))).itemStack();
    }

    /** A named, lored, enchanted snowball - a kit projectile. The reskin must keep every one of these, changing only the base type. */
    private static ItemStack fancySnowball() {
        return ItemStack.of(Material.SNOWBALL, 16)
                .withCustomName(Component.text("Feather"))
                .withLore(Component.text("Kit projectile"))
                .with(DataComponents.ENCHANTMENTS, new EnchantmentList(RegistryKey.<Enchantment>unsafeOf(Key.key("minecraft:power")), 2));
    }

    @Test
    void reskinsThrowableToNonUsableBaseInClientView() {
        CompatState s = new CompatState();
        s.apply(Compat18.config()); // suppressThrowSwing on
        assertTrue(s.suppressesThrowSwing());
        ItemStack shown = slotItem(s, ItemStack.of(Material.SNOWBALL, 16));
        assertEquals(Material.PAPER, shown.material(), "the client sees a non-usable base (no throw swing)");
        assertEquals("minecraft:snowball", shown.get(DataComponents.ITEM_MODEL), "but it still renders as a snowball");
        assertEquals(16, shown.amount(), "count preserved");
        assertEquals(ItemStack.of(Material.SNOWBALL).get(DataComponents.ITEM_NAME), shown.get(DataComponents.ITEM_NAME),
                "and reads as a snowball, not \"Paper\"");
        assertEquals(16, shown.get(DataComponents.MAX_STACK_SIZE), "and stacks like a snowball, not paper's 64");
    }

    @Test
    void reskinPreservesRealNameLoreEnchants() {
        CompatState s = new CompatState();
        s.apply(Compat18.config());
        ItemStack shown = slotItem(s, fancySnowball());
        assertEquals(Material.PAPER, shown.material());
        assertEquals(Component.text("Feather"), shown.get(DataComponents.CUSTOM_NAME), "the item's real name is kept (not overwritten)");
        assertEquals(1, shown.get(DataComponents.LORE).size(), "lore is kept");
        assertNotNull(shown.get(DataComponents.ENCHANTMENTS), "enchantments are kept");
    }

    @Test
    void restoresEchoedReskinToTrueItem() {
        CompatState s = new CompatState();
        s.apply(Compat18.config());
        ItemStack restored = s.sanitizeInboundItem(slotItem(s, ItemStack.of(Material.SNOWBALL, 16))); // creative echo of the reskinned paper
        assertEquals(Material.SNOWBALL, restored.material(), "a creative-echoed reskin becomes the true snowball again");
        assertNull(restored.get(DataComponents.ITEM_MODEL), "the reskin marker is cleared (renders as a plain snowball)");
        assertEquals(ItemStack.of(Material.SNOWBALL).get(DataComponents.ITEM_NAME), restored.get(DataComponents.ITEM_NAME), "and reads as a snowball");
        assertEquals(16, restored.amount());
    }

    @Test
    void restoreKeepsRealComponents() {
        CompatState s = new CompatState();
        s.apply(Compat18.config());
        ItemStack restored = s.sanitizeInboundItem(slotItem(s, fancySnowball()));
        assertEquals(Material.SNOWBALL, restored.material());
        assertEquals(Component.text("Feather"), restored.get(DataComponents.CUSTOM_NAME), "a real name survives the creative round-trip");
        assertNotNull(restored.get(DataComponents.ENCHANTMENTS), "so do enchantments");
        assertNull(restored.get(DataComponents.ITEM_MODEL));
    }

    @Test
    void leavesThrowablesUnchangedWhenNotSuppressing() {
        CompatState s = new CompatState(); // OFF policy
        assertFalse(s.suppressesThrowSwing());
        assertEquals(Material.SNOWBALL, slotItem(s, ItemStack.of(Material.SNOWBALL)).material(),
                "a modern client without the fix keeps the real snowball (and its vanilla throw swing)");
    }
}
