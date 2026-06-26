package io.github.term4.minestommechanics;

import io.github.term4.minestommechanics.mechanics.attack.AttackConfig;
import io.github.term4.minestommechanics.mechanics.attribute.AttributeConfig;
import io.github.term4.minestommechanics.mechanics.blocking.BlockingConfig;
import io.github.term4.minestommechanics.mechanics.consumable.ConsumableConfig;
import io.github.term4.minestommechanics.mechanics.damage.DamageConfig;
import io.github.term4.minestommechanics.mechanics.durability.DurabilityConfig;
import io.github.term4.minestommechanics.mechanics.hunger.HungerConfig;
import io.github.term4.minestommechanics.mechanics.knockback.KnockbackConfig;
import io.github.term4.minestommechanics.mechanics.projectile.ProjectileConfig;
import io.github.term4.minestommechanics.item.ItemRegistry;
import io.github.term4.minestommechanics.platform.compatibility.CompatConfig;
import io.github.term4.minestommechanics.platform.fixes.FixesConfig;
import io.github.term4.minestommechanics.platform.player.PlayerConfig;
import io.github.term4.minestommechanics.tracking.motion.VelocityRule;
import io.github.term4.minestommechanics.util.tick.TickScalingConfig;

/**
 * Catalog of the built-in {@link MechanicsProfile} config keys. A user module adds a member by declaring its own
 * {@link ConfigKey} the same way (no change here) and using {@link MechanicsProfile.Builder#set} / {@link MechanicsProfile#get}.
 */
public final class MechanicsKeys {

    private MechanicsKeys() {}

    public static final ConfigKey<AttackConfig> ATTACK = ConfigKey.of("mm:attack");
    public static final ConfigKey<DamageConfig> DAMAGE = ConfigKey.of("mm:damage");
    public static final ConfigKey<KnockbackConfig> KNOCKBACK = ConfigKey.of("mm:knockback");
    public static final ConfigKey<PlayerConfig> PLAYER = ConfigKey.of("mm:player");
    public static final ConfigKey<VelocityRule> VELOCITY = ConfigKey.of("mm:velocity");
    public static final ConfigKey<ProjectileConfig> PROJECTILES = ConfigKey.of("mm:projectiles");
    public static final ConfigKey<FixesConfig> FIXES = ConfigKey.of("mm:fixes");
    public static final ConfigKey<AttributeConfig> ATTRIBUTES = ConfigKey.of("mm:attributes");
    public static final ConfigKey<TickScalingConfig> TICK_SCALING = ConfigKey.of("mm:tick-scaling");
    public static final ConfigKey<DurabilityConfig> DURABILITY = ConfigKey.of("mm:durability");
    public static final ConfigKey<HungerConfig> HUNGER = ConfigKey.of("mm:hunger");
    public static final ConfigKey<ConsumableConfig> CONSUMABLES = ConfigKey.of("mm:consumables");
    public static final ConfigKey<BlockingConfig> BLOCKING = ConfigKey.of("mm:blocking");
    public static final ConfigKey<CompatConfig> COMPAT = ConfigKey.of("mm:compat");
    public static final ConfigKey<ItemRegistry> ITEMS = ConfigKey.of("mm:items");
}
