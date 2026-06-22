package io.github.term4.minestommechanics.mechanics;

import io.github.term4.minestommechanics.mechanics.attribute.AttributeConfig;
import io.github.term4.minestommechanics.mechanics.attribute.defense.ArmorConfig;
import io.github.term4.minestommechanics.mechanics.attribute.defense.ProtectionConfig;
import io.github.term4.minestommechanics.mechanics.attribute.catalog.VanillaAttributes;
import io.github.term4.minestommechanics.mechanics.attribute.catalog.enchant.Sharpness;
import io.github.term4.minestommechanics.mechanics.attribute.catalog.effect.Absorption;
import io.github.term4.minestommechanics.mechanics.attribute.catalog.effect.Haste;
import io.github.term4.minestommechanics.mechanics.attribute.catalog.effect.JumpBoost;
import io.github.term4.minestommechanics.mechanics.attribute.catalog.effect.MiningFatigue;
import io.github.term4.minestommechanics.mechanics.attribute.catalog.effect.Strength;
import io.github.term4.minestommechanics.mechanics.attribute.catalog.effect.Weakness;
import io.github.term4.minestommechanics.item.ItemDef;
import io.github.term4.minestommechanics.item.ItemRegistry;
import io.github.term4.minestommechanics.item.VanillaItems;
import io.github.term4.minestommechanics.mechanics.consumable.ConsumableConfig;
import io.github.term4.minestommechanics.mechanics.consumable.ConsumableTypeConfig;
import io.github.term4.minestommechanics.mechanics.consumable.catalog.VanillaConsumables;
import io.github.term4.minestommechanics.mechanics.damage.DamageConfig;
import io.github.term4.minestommechanics.mechanics.damage.types.DamageTypeConfig;
import io.github.term4.minestommechanics.mechanics.damage.types.breathing.BreathingConfig;
import io.github.term4.minestommechanics.mechanics.damage.types.breathing.DrowningDamage;
import io.github.term4.minestommechanics.mechanics.damage.types.breathing.SuffocationDamage;
import io.github.term4.minestommechanics.mechanics.damage.types.burning.BurningConfig;
import io.github.term4.minestommechanics.mechanics.damage.types.burning.BurningDamage;
import io.github.term4.minestommechanics.mechanics.damage.types.burning.InFireDamage;
import io.github.term4.minestommechanics.mechanics.damage.types.burning.LavaDamage;
import io.github.term4.minestommechanics.mechanics.damage.types.fall.FallDamageConfig;
import io.github.term4.minestommechanics.mechanics.projectile.ProjectileConfig;
import io.github.term4.minestommechanics.mechanics.projectile.types.Arrow;
import io.github.term4.minestommechanics.mechanics.projectile.types.Egg;
import io.github.term4.minestommechanics.mechanics.projectile.types.Pearl;
import io.github.term4.minestommechanics.mechanics.projectile.types.ProjectileTypeConfig;
import io.github.term4.minestommechanics.mechanics.projectile.types.ProjectileTypeConfig.HitResponse;
import io.github.term4.minestommechanics.mechanics.projectile.types.ProjectileTypeConfig.PhysicsOrder;
import io.github.term4.minestommechanics.mechanics.projectile.types.Snowball;
import io.github.term4.minestommechanics.tracking.motion.ClimbModel;
import io.github.term4.minestommechanics.tracking.motion.VelocityConfig;
import io.github.term4.minestommechanics.tracking.motion.VelocityRule;
import io.github.term4.minestommechanics.tracking.motion.FluidFlow;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.attribute.Attribute;
import net.minestom.server.potion.PotionEffect;

/**
 * Modern (26.1+) preset factory - damage and {@link #projectiles() projectiles} are implemented; combat/knockback
 * are still TODO. {@link Vanilla18} holds the 1.8 baselines.
 *
 * <p>Fall damage uses {@link FallDamageConfig.Formula#MODERN_FLOOR}; burning contact amounts match 1.8 but the ignite
 * warmup defaults to {@code 3 * invulTicks} (vs {@code 2 *} in {@link Vanilla18}). Still TODO in the fall producer:
 * fall-immunity tag, Slow Falling, elytra fly, explosion-impulse grace, and the special landing scans (hay / bed /
 * powder snow / cobweb).
 */
public final class Vanilla {
    private Vanilla() {}

    /**
     * Modern (26.1) velocity tracking method: server-arc reconstruction with the pure-modern fluid current
     * ({@link FluidFlow.Model#MODERN} - averaged + depth-scaled water and lava, {@link VelocityConfig#flowLava} on).
     * Set on a {@code MechanicsProfile.velocity(...)} scope. ({@link Vanilla18#velocity()} is the flat 1.8 {@code LEGACY}.)
     */
    public static VelocityRule velocity() {
        return VelocityRule.simulated(VelocityConfig.builder()
                .flowModel(FluidFlow.Model.MODERN)
                .climbModel(ClimbModel.MODERN)
                .modernBlockPhysics(true)
                .build());
    }

    /**
     * Modern (26.1) attribute config: MODERN flavor + the modern source variants. Unlike {@link Vanilla18#attributes()},
     * this includes Haste / Mining Fatigue (attack-speed) and Jump Boost (safe-fall-distance), which are attribute-based
     * in 26 but dig-speed / jump-velocity mechanics in 1.8 (so absent from the legacy catalog until those systems exist).
     */
    public static AttributeConfig attributes() {
        return AttributeConfig.builder()
                .sources(Strength.MODERN, Weakness.MODERN, Sharpness.MODERN, Absorption.MODERN) // 26 version variants
                .sources(VanillaAttributes.enchants())
                .sources(VanillaAttributes.effects())
                .sources(Haste.MODERN, MiningFatigue.MODERN, JumpBoost.MODERN) // modern-only: Via strips these for 1.8
                .armor(ArmorConfig.builder().formula(ArmorConfig.Formula.MODERN_TOUGHNESS).build())
                .protection(ProtectionConfig.builder().formula(ProtectionConfig.Formula.MODERN_LINEAR).build())
                .build();
    }

    /**
     * Modern (26) consumables: the golden apples with 26-source effects (the registered
     * {@link VanillaConsumables#types() types} get their behavior here). Regular = Regen II (5s) + Absorption I (2m);
     * enchanted = Regen II (20s) + Resistance (5m) + Fire Resistance (5m) + Absorption IV (2m). Both restore 4 food +
     * 9.6 saturation. Differs from {@link Vanilla18#consumables()} (1.8: enchanted is Regen V + Absorption I).
     */
    public static ConsumableConfig consumables() {
        return ConsumableConfig.builder()
                .typeConfigs(
                        ConsumableTypeConfig.builder(VanillaConsumables.GOLDEN_APPLE.key())
                                .behavior(VanillaConsumables.effectFood(4, 9.6f,
                                        VanillaConsumables.eff(PotionEffect.REGENERATION, 2, 100),
                                        VanillaConsumables.eff(PotionEffect.ABSORPTION, 1, 2400)))
                                .build(),
                        ConsumableTypeConfig.builder(VanillaConsumables.ENCHANTED_GOLDEN_APPLE.key())
                                .behavior(VanillaConsumables.effectFood(4, 9.6f,
                                        VanillaConsumables.eff(PotionEffect.REGENERATION, 2, 400),
                                        VanillaConsumables.eff(PotionEffect.RESISTANCE, 1, 6000),
                                        VanillaConsumables.eff(PotionEffect.FIRE_RESISTANCE, 1, 6000),
                                        VanillaConsumables.eff(PotionEffect.ABSORPTION, 4, 2400)))
                                .build())
                .build();
    }

    /** Modern (26) item registry: MODERN version - weapon attack damage derives from Minestom's {@code ATTACK_DAMAGE}. */
    public static ItemRegistry items() {
        return new ItemRegistry(ItemDef.Version.MODERN, VanillaItems.weapons());
    }

    /** Damage config with modern fall formula and 26.1 burning behaviour. */
    public static DamageConfig dmg() {
        return DamageConfig.builder()
                .typeConfigs(
                        fallDamage(),
                        inFireDamage(),
                        lavaDamage(),
                        burningDamage(),
                        drownDamage(),
                        suffocationDamage()
                )
                .build();
    }

    /** Modern (26) drowning: 2.0 at air {@code <= -20}, air recovers {@code +4}/tick ({@code AirRefill.MODERN}). */
    private static BreathingConfig drownDamage() {
        return BreathingConfig.builder()
                .key(DrowningDamage.KEY)
                .baseAmount(2.0)
                .airRefill(BreathingConfig.AirRefill.MODERN)
                .build();
    }

    /** Vanilla suffocation: 1.0 per tick while the head is in a solid block (same 1.8 + 26). */
    private static DamageTypeConfig suffocationDamage() {
        return DamageTypeConfig.builder(SuffocationDamage.KEY).baseAmount(1.0).build();
    }

    private static FallDamageConfig fallDamage() {
        return FallDamageConfig.builder()
                .formula(FallDamageConfig.Formula.MODERN_FLOOR)
                .bypassArmor(true) // fall is in the BYPASSES_ARMOR tag (26); only Feather Falling (EPF) reduces it
                // safe distance is the victim's SAFE_FALL_DISTANCE attribute (default 3.0); Jump Boost pushes +1/level onto it
                .threshold(ctx -> ctx.snap().target() instanceof LivingEntity le
                        ? le.getAttributeValue(Attribute.SAFE_FALL_DISTANCE) : 3.0)
                .damageModifier(1.0)
                .fallDamageMultiplier(1.0)
                .build();
    }

    private static BurningConfig inFireDamage() {
        return BurningConfig.builder()
                .key(InFireDamage.KEY)
                .baseAmount(1.0)
                .igniteTicks(160)
                .igniteWarmupInvulMult(3)
                .contactIntervalTicks(1)
                .build();
    }

    private static BurningConfig lavaDamage() {
        return BurningConfig.builder()
                .key(LavaDamage.KEY)
                .baseAmount(4.0)
                .igniteTicks(300)
                .igniteWarmupInvulMult(3)
                .contactIntervalTicks(1)
                .build();
    }

    private static BurningConfig burningDamage() {
        return BurningConfig.builder()
                .key(BurningDamage.KEY)
                .baseAmount(1.0)
                .intervalTicks(20)
                .skipBurnWhileInLava(true)
                .bypassArmor(true) // on_fire is in the BYPASSES_ARMOR tag (26); in_fire/lava aren't
                .build();
    }

    // =========================================================================
    // Projectiles (26.1)
    // =========================================================================

    /**
     * Modern (26.1) projectile config: the {@link #projectileDefaults()} baseline plus per-type entries. The pearl
     * keeps its self-pass-through; the arrow overrides physics ({@link #arrow()}). Snowball and egg are pure baseline.
     */
    public static ProjectileConfig projectiles() {
        return ProjectileConfig.builder()
                .defaults(projectileDefaults())
                .typeConfigs(
                        ProjectileTypeConfig.builder(Snowball.KEY).build(),
                        ProjectileTypeConfig.builder(Egg.KEY).build(),
                        ProjectileTypeConfig.builder(Pearl.KEY).selfHit(HitResponse.PASS_THROUGH).build(),
                        arrow())
                .build();
    }

    /**
     * The 26.1 throwable baseline: {@link Vanilla18#projectileDefaults() the 1.8 baseline} with the documented deltas -
     * no throwing-hand lateral, full shooter-momentum inheritance (vertical only when airborne), {@code REVERSE}
     * deflection ({@code motion *= -0.5} + a cosmetic +-10-degree yaw), drag + gravity before the move, and shooter
     * immunity until the projectile clears the shooter's box. Everything else (speed, spread, gravity/drag, knockback,
     * damage) matches 1.8.
     */
    public static ProjectileTypeConfig projectileDefaults() {
        return modernDeltas(ProjectileTypeConfig.builder(Vanilla18.projectileDefaults())).build();
    }

    /**
     * Vanilla 26.1 arrow: {@link Vanilla18#arrow() the 1.8 arrow} (speed 3.0, gravity 0.05, velocity-based damage,
     * sticks in blocks) with the same 26.1 deltas as {@link #projectileDefaults()}, plus {@code invulnHit(DEFLECT)} -
     * 26.1 deflects a rejected hit for both the creative-target and invul-window cases (no creative pass-through, unlike
     * 1.8), so one constant covers both.
     */
    public static ProjectileTypeConfig arrow() {
        return modernDeltas(ProjectileTypeConfig.builder(Vanilla18.arrow()))
                .invulnHit(HitResponse.DEFLECT)
                .build();
    }

    /** Applies the shared 1.8 -&gt; 26.1 projectile deltas onto {@code b} (over a {@link Vanilla18} base). */
    private static ProjectileTypeConfig.Builder modernDeltas(ProjectileTypeConfig.Builder b) {
        return b
                .spawnOffsetSideways(0.0)
                .momentumHorizontal(1.0)
                // 26.1 momentum = the shooter's client motion, read at launch
                .momentumVertical(ctx -> ctx.shooter().isOnGround() ? 0.0 : 1.0) // 26.1 folds vertical only when airborne
                .deflect(-0.5, 0, -10, 10)
                .physicsOrder(PhysicsOrder.DRAG_BEFORE_MOVE)
                .leftOwnerImmunity(true);
    }
}

