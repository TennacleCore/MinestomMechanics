package io.github.term4.minestommechanics.mechanics.attribute.combat;

import io.github.term4.minestommechanics.Services;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.item.ItemStack;

/**
 * The context handed to an {@link OnHit} enchant when its holder lands a hit: who hit whom, the enchant {@code level},
 * the {@code item} the enchant rode in on, and {@code services} for any system the effect needs (e.g. a Thorns reflect
 * goes back through {@code services.damage()}).
 */
public record HitContext(LivingEntity attacker, LivingEntity victim, int level, ItemStack item, Services services) {}
