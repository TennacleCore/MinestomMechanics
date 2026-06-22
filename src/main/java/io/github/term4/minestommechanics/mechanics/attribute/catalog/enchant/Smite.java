package io.github.term4.minestommechanics.mechanics.attribute.catalog.enchant;

import io.github.term4.minestommechanics.mechanics.attribute.Attribute;
import io.github.term4.minestommechanics.mechanics.attribute.AttributeConfigResolver.AttributeContext;
import io.github.term4.minestommechanics.mechanics.attribute.source.ItemSource;
import io.github.term4.minestommechanics.mechanics.attribute.source.Source;
import io.github.term4.minestommechanics.mechanics.attribute.combat.CombatFacts;
import io.github.term4.minestommechanics.mechanics.attribute.combat.MonsterTypes;
import net.kyori.adventure.key.Key;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.attribute.AttributeOperation;

import java.util.List;

/**
 * Smite (enchant) - a flat melee bonus <em>vs undead</em>, like {@link Sharpness} but victim-conditional. {@code 2.5 × level}
 * ({@code minecraft:damage} data effect / 1.8 {@code EnchantmentWeaponDamage}, identical), gated by the combat
 * {@link CombatFacts#TARGET target} being undead. Reads the target off the context - no attacker/victim plumbing in the
 * attribute layer; the melee path drops the fact in. An {@link ItemSource}: read from the weapon on demand.
 */
public final class Smite {

    public static final Key KEY = Key.key("minecraft:smite");
    /** Flat bonus per level vs undead. */
    private static final double PER_LEVEL = 2.5;

    private Smite() {}

    public static final Source INSTANCE = new ItemSource(KEY) {
        @Override public List<Mod> modifiers(int level, AttributeContext ctx) {
            Entity target = ctx.fact(CombatFacts.TARGET);
            if (level <= 0 || !MonsterTypes.isUndead(target)) return List.of();
            return List.of(new Mod(Attribute.MELEE_FLAT_ADD, AttributeOperation.ADD_VALUE, PER_LEVEL * level));
        }
    };
}
