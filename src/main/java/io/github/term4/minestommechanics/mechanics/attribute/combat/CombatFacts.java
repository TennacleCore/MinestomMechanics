package io.github.term4.minestommechanics.mechanics.attribute.combat;

import io.github.term4.minestommechanics.mechanics.attribute.FactKey;
import net.minestom.server.entity.Entity;

/**
 * Combat-domain facts the attack path drops onto an {@code AttributeContext}, for victim-conditional offensive enchants
 * (Smite/Bane). The value is a neutral {@link Entity}, so a catalog enchant reads the target without importing the damage
 * package (keeping the attribute package isolated). Other domains define their own fact keys the same way.
 */
public final class CombatFacts {
    private CombatFacts() {}

    /** The entity being attacked (the melee/projectile victim). Absent outside a combat read. */
    public static final FactKey<Entity> TARGET = new FactKey<>("mm:combat/target");
}
