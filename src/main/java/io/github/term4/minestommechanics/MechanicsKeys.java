package io.github.term4.minestommechanics;

import io.github.term4.minestommechanics.mechanics.attack.AttackConfig;
import io.github.term4.minestommechanics.mechanics.attribute.AttributeConfig;
import io.github.term4.minestommechanics.mechanics.blocking.BlockingConfig;
import io.github.term4.minestommechanics.mechanics.cooldown.CooldownConfig;
import io.github.term4.minestommechanics.mechanics.consumable.ConsumableConfig;
import io.github.term4.minestommechanics.mechanics.damage.DamageConfig;
import io.github.term4.minestommechanics.mechanics.death.DeathConfig;
import io.github.term4.minestommechanics.mechanics.durability.DurabilityConfig;
import io.github.term4.minestommechanics.fx.FxRegistry;
import io.github.term4.minestommechanics.mechanics.explosion.ExplosionConfig;
import io.github.term4.minestommechanics.mechanics.hunger.HungerConfig;
import io.github.term4.minestommechanics.mechanics.knockback.KnockbackConfig;
import io.github.term4.minestommechanics.mechanics.projectile.ProjectileConfig;
import io.github.term4.minestommechanics.item.ItemRegistry;
import io.github.term4.minestommechanics.platform.compatibility.CompatConfig;
import io.github.term4.minestommechanics.platform.fixes.FixesConfig;
import io.github.term4.minestommechanics.platform.player.PlayerConfig;
import io.github.term4.minestommechanics.tracking.motion.VelocityRule;
import io.github.term4.minestommechanics.entity.DroppedItemEntity;
import io.github.term4.minestommechanics.vri.VriConfig;
import io.github.term4.minestommechanics.util.tick.TickScalingConfig;

/** Catalog of the built-in {@link MechanicsProfile} config keys. */
public final class MechanicsKeys {

    private MechanicsKeys() {}

    public static final ConfigKey<AttackConfig> ATTACK = ConfigKey.of("mm:attack", AttackConfig.class);
    public static final ConfigKey<DamageConfig> DAMAGE = ConfigKey.of("mm:damage", DamageConfig.class);
    public static final ConfigKey<DeathConfig> DEATH = ConfigKey.of("mm:death", DeathConfig.class);
    public static final ConfigKey<KnockbackConfig> KNOCKBACK = ConfigKey.of("mm:knockback", KnockbackConfig.class);
    public static final ConfigKey<PlayerConfig> PLAYER = ConfigKey.of("mm:player", PlayerConfig.class);
    public static final ConfigKey<VelocityRule> VELOCITY = ConfigKey.of("mm:velocity", VelocityRule.class);
    public static final ConfigKey<ProjectileConfig> PROJECTILES = ConfigKey.of("mm:projectiles", ProjectileConfig.class);
    public static final ConfigKey<FixesConfig> FIXES = ConfigKey.of("mm:fixes", FixesConfig.class);
    public static final ConfigKey<AttributeConfig> ATTRIBUTES = ConfigKey.of("mm:attributes", AttributeConfig.class);
    public static final ConfigKey<TickScalingConfig> TICK_SCALING = ConfigKey.of("mm:tick-scaling", TickScalingConfig.class);
    public static final ConfigKey<DurabilityConfig> DURABILITY = ConfigKey.of("mm:durability", DurabilityConfig.class);
    public static final ConfigKey<HungerConfig> HUNGER = ConfigKey.of("mm:hunger", HungerConfig.class);
    public static final ConfigKey<ConsumableConfig> CONSUMABLES = ConfigKey.of("mm:consumables", ConsumableConfig.class);
    public static final ConfigKey<FxRegistry> FX = ConfigKey.of("mm:fx", FxRegistry.class);
    public static final ConfigKey<BlockingConfig> BLOCKING = ConfigKey.of("mm:blocking", BlockingConfig.class);
    /** Server-authoritative item-use cooldowns. */
    public static final ConfigKey<CooldownConfig> COOLDOWNS = ConfigKey.of("mm:item-cooldowns", CooldownConfig.class);
    /** Vanilla behaviors Minestom omits (crack overlay, block drops, item pickup/drop). */
    public static final ConfigKey<VriConfig> VRI = ConfigKey.of("mm:vri", VriConfig.class);
    /** Dropped-item environment physics: 1.8 sink vs 26.1 float. */
    public static final ConfigKey<DroppedItemEntity.Model> ITEM_PHYSICS = ConfigKey.of("mm:item-physics", DroppedItemEntity.Model.class);
    public static final ConfigKey<ExplosionConfig> EXPLOSION = ConfigKey.of("mm:explosion", ExplosionConfig.class);
    public static final ConfigKey<CompatConfig> COMPAT = ConfigKey.of("mm:compat", CompatConfig.class);
    public static final ConfigKey<ItemRegistry> ITEMS = ConfigKey.of("mm:items", ItemRegistry.class);
}
