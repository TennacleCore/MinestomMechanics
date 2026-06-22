package io.github.term4.minestommechanics.item;

import net.minestom.server.component.DataComponents;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.attribute.Attribute;
import net.minestom.server.entity.attribute.AttributeInstance;
import net.minestom.server.entity.attribute.AttributeModifier;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.component.AttributeList;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiFunction;

/**
 * A named, extensible item-stat key for {@link ItemRegistry} lookups. Each stat also knows how to derive its value from
 * Minestom (the {@code minestomDefault}) so an unregistered item falls back to what Minestom already computes rather
 * than a duplicated table - that's the "don't re-store what Minestom gets right" rule. Add a new stat = one constant here.
 */
public final class ItemStat {

    /**
     * Held-weapon attack damage: the item's <em>intrinsic</em> value (attribute base + the item's own {@code ATTACK_DAMAGE}
     * modifiers), <em>excluding</em> the holder's potion-effect modifiers. The melee calculator folds Strength/Weakness
     * once through the attribute system, so reading the holder's full attribute value here would double-count them - a
     * held item would, unlike a fist (which uses the effect-free fallback), get e.g. Weakness twice.
     */
    public static final ItemStat ATTACK_DAMAGE = new ItemStat("attack_damage", ItemStat::weaponAttackDamage);

    /** Base attack-damage attribute value + the {@code item}'s own ATTACK_DAMAGE modifiers (no effect/holder modifiers). */
    private static double weaponAttackDamage(ItemStack item, @Nullable LivingEntity holder) {
        if (holder == null) return Double.NaN;
        AttributeInstance inst = holder.getAttribute(Attribute.ATTACK_DAMAGE);
        if (inst == null) return Double.NaN;
        double base = inst.getBaseValue(), add = 0, multBase = 0, multTotal = 1;
        AttributeList list = item.get(DataComponents.ATTRIBUTE_MODIFIERS);
        if (list != null) for (AttributeList.Modifier m : list.modifiers()) {
            if (!m.attribute().equals(Attribute.ATTACK_DAMAGE)) continue;
            AttributeModifier mod = m.modifier();
            switch (mod.operation()) {
                case ADD_VALUE -> add += mod.amount();
                case ADD_MULTIPLIED_BASE -> multBase += mod.amount();
                case ADD_MULTIPLIED_TOTAL -> multTotal *= (1 + mod.amount());
            }
        }
        return (base + add) * (1 + multBase) * multTotal;
    }

    // future, one line each as the attributes come online:
    // public static final ItemStat ATTACK_SPEED = new ItemStat("attack_speed", ...);
    // public static final ItemStat MINING_SPEED = new ItemStat("mining_speed", ...);

    private final String id;
    private final BiFunction<ItemStack, @Nullable LivingEntity, Double> minestomDefault;

    private ItemStat(String id, BiFunction<ItemStack, @Nullable LivingEntity, Double> minestomDefault) {
        this.id = id;
        this.minestomDefault = minestomDefault;
    }

    public String id() { return id; }

    /** The Minestom-derived value for this stat, or {@code NaN} when Minestom can't supply it (then the caller's fallback wins). */
    double minestomDefault(ItemStack item, @Nullable LivingEntity holder) {
        return minestomDefault.apply(item, holder);
    }

    @Override public String toString() { return "ItemStat[" + id + "]"; }
}
