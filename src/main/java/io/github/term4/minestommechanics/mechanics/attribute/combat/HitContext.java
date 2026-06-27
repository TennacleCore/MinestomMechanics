package io.github.term4.minestommechanics.mechanics.attribute.combat;

import io.github.term4.minestommechanics.Services;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.item.ItemStack;

/**
 * The context handed to an {@link OnHit} enchant when its holder lands a hit. {@code services} lets the effect route into
 * any system it needs (e.g. a reflect back through {@code services.damage()}).
 */
public record HitContext(LivingEntity attacker, LivingEntity victim, int level, ItemStack item, Services services) {}
