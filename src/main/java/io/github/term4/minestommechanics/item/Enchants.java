package io.github.term4.minestommechanics.item;

import net.kyori.adventure.key.Key;
import net.minestom.server.component.DataComponents;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.component.EnchantmentList;
import net.minestom.server.item.enchant.Enchantment;
import net.minestom.server.registry.RegistryKey;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Reads an enchantment level off an item's {@code ENCHANTMENTS} component by its {@link Key} - item-data, so it lives
 * with the rest of the item helpers ({@link ItemRegistry}/{@link ItemStat}). The shared reader the domains that consume
 * enchant data use (the bow's Infinity, the projectile Power/Punch capture, the attribute equipment lifecycle, the melee
 * Knockback enchant), so the component walk lives in one place instead of being re-copied per consumer.
 */
public final class Enchants {

    private Enchants() {}

    /** Level of {@code key} on {@code stack}, or {@code 0} if absent / air / unenchanted. */
    public static int level(@Nullable ItemStack stack, Key key) {
        if (stack == null || stack.isAir()) return 0;
        EnchantmentList list = stack.get(DataComponents.ENCHANTMENTS);
        if (list == null) return 0;
        for (Map.Entry<RegistryKey<Enchantment>, Integer> e : list.enchantments().entrySet()) {
            if (e.getKey().key().equals(key)) return e.getValue();
        }
        return 0;
    }
}
