package io.github.term4.minestommechanics.mechanics;

import io.github.term4.minestommechanics.Services;
import io.github.term4.minestommechanics.platform.player.OptimizedPlayer;
import io.github.term4.minestommechanics.api.event.AttackEvent;
import io.github.term4.minestommechanics.mechanics.attack.AttackConfig;
import io.github.term4.minestommechanics.mechanics.attribute.AttributeConfig;
import io.github.term4.minestommechanics.mechanics.attribute.defense.ArmorConfig;
import io.github.term4.minestommechanics.mechanics.attribute.defense.ProtectionConfig;
import io.github.term4.minestommechanics.mechanics.attribute.catalog.VanillaAttributes;
import io.github.term4.minestommechanics.mechanics.attribute.catalog.enchant.Knockback;
import io.github.term4.minestommechanics.mechanics.attribute.catalog.enchant.Sharpness;
import io.github.term4.minestommechanics.mechanics.attribute.catalog.effect.Absorption;
import io.github.term4.minestommechanics.mechanics.attribute.catalog.effect.Strength;
import io.github.term4.minestommechanics.mechanics.attribute.catalog.effect.Weakness;
import io.github.term4.minestommechanics.item.Enchants;
import io.github.term4.minestommechanics.item.ItemDef;
import io.github.term4.minestommechanics.item.ItemRegistry;
import io.github.term4.minestommechanics.item.VanillaItems;
import io.github.term4.minestommechanics.mechanics.blocking.BlockingBehavior;
import io.github.term4.minestommechanics.mechanics.blocking.BlockingConfig;
import io.github.term4.minestommechanics.mechanics.blocking.BlockingTypeConfig;
import io.github.term4.minestommechanics.mechanics.blocking.catalog.VanillaBlocking;
import io.github.term4.minestommechanics.mechanics.consumable.ConsumableConfig;
import io.github.term4.minestommechanics.mechanics.consumable.ConsumableTypeConfig;
import io.github.term4.minestommechanics.mechanics.consumable.catalog.VanillaConsumables;
import io.github.term4.minestommechanics.mechanics.damage.DamageSnapshot;
import io.github.term4.minestommechanics.mechanics.damage.DamageConfig;
import io.github.term4.minestommechanics.mechanics.damage.DamageSystem;
import io.github.term4.minestommechanics.mechanics.damage.types.DamageTypeConfig;
import io.github.term4.minestommechanics.mechanics.damage.types.burning.BurningConfig;
import io.github.term4.minestommechanics.mechanics.damage.types.burning.BurningDamage;
import io.github.term4.minestommechanics.mechanics.damage.types.burning.InFireDamage;
import io.github.term4.minestommechanics.mechanics.damage.types.burning.LavaDamage;
import io.github.term4.minestommechanics.mechanics.damage.types.breathing.BreathingConfig;
import io.github.term4.minestommechanics.mechanics.damage.types.breathing.DrowningDamage;
import io.github.term4.minestommechanics.mechanics.damage.types.breathing.SuffocationDamage;
import io.github.term4.minestommechanics.mechanics.damage.types.cactus.CactusDamage;
import io.github.term4.minestommechanics.mechanics.damage.types.fall.FallDamageConfig;
import io.github.term4.minestommechanics.mechanics.damage.types.melee.MeleeDamage;
import io.github.term4.minestommechanics.mechanics.damage.types.melee.MeleeDamageConfig;
import io.github.term4.minestommechanics.mechanics.knockback.KnockbackConfig;
import io.github.term4.minestommechanics.mechanics.knockback.KnockbackSnapshot;
import io.github.term4.minestommechanics.mechanics.knockback.KnockbackSystem;
import io.github.term4.minestommechanics.mechanics.damage.types.projectile.ProjectileDamage;
import io.github.term4.minestommechanics.mechanics.projectile.ProjectileConfig;
import io.github.term4.minestommechanics.mechanics.projectile.types.Arrow;
import io.github.term4.minestommechanics.mechanics.projectile.types.Egg;
import io.github.term4.minestommechanics.mechanics.projectile.types.Pearl;
import io.github.term4.minestommechanics.mechanics.projectile.types.ProjectileTypeConfig;
import io.github.term4.minestommechanics.mechanics.projectile.types.Snowball;
import io.github.term4.minestommechanics.platform.player.PlayerConfig;
import io.github.term4.minestommechanics.tracking.motion.MotionTracker;
import io.github.term4.minestommechanics.tracking.SprintTracker;
import io.github.term4.minestommechanics.tracking.motion.VelocityRule;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.Player;
import net.minestom.server.potion.PotionEffect;

/** Preconfigured vanilla 1.8 values. Use these as defaults when resolving nullable configs. */
public final class Vanilla18 {

    private Vanilla18() {}

    /**
     * Vanilla 1.8 velocity tracking method (the {@code motX/motY/motZ} reconstruction the friction fold and hurt
     * broadcast read). Set on a {@code MechanicsProfile.velocity(...)} scope rather than per config.
     *
     * <p>The attacker self-slowdown (horizontal {@code *= 0.6} on a landed sprint/enchant hit) is implemented on
     * {@link io.github.term4.minestommechanics.mechanics.attack.AttackConfig#fullHitScale} - velocity-only, gated
     * separately from victim knockback. Vanilla {@code 0.6}; Mmc18 {@code 1.0} (no slowdown).
     */
    public static VelocityRule velocity() {
        return VelocityRule.simulated();
    }

    /**
     * Vanilla 1.8 projectile config: the generic {@link #projectileDefaults()} baseline plus per-type entries
     * (presence enables a type at install). All three throwables share the baseline; their differences are in the
     * flight entity (egg -&gt; baby chicken, pearl -&gt; teleport). Add arrow once ported (it overrides physics).
     */
    public static ProjectileConfig projectiles() {
        return ProjectileConfig.builder()
                .defaults(projectileDefaults())
                .typeConfigs(
                        ProjectileTypeConfig.builder(Snowball.KEY).build(),
                        ProjectileTypeConfig.builder(Egg.KEY).build(),
                        pearl(),
                        arrow())
                .build();
    }

    /**
     * The generic vanilla 1.8 throwable baseline every type inherits unless it overrides a knob: zero render box
     * (block collision is a point raytrace), aerodynamics (gravity {@code 0.03}, drag {@code 0.99}), speed {@code 1.5},
     * spread {@code 0.0075}, no shooter-momentum inheritance, spawn {@code 0.1} below the eye + {@code 0.16} lateral,
     * 5-tick shooter immunity then normal self-hits (the pearl overrides with {@code selfHit(PASS_THROUGH)}), 20-tick
     * sync, {@link #kb() vanilla knockback} from the thrower, and 0 damage routed through {@link ProjectileDamage}
     * (hurt flash + invul gate; a 0-damage hit still lands). Re-base per-type via
     * {@code ProjectileTypeConfig.builder(Vanilla18.projectileDefaults())...}.
     */
    public static ProjectileTypeConfig projectileDefaults() {
        return ProjectileTypeConfig.builder()
                .boundingBox(0, 0, 0)
                .gravity(0.03).horizontalDrag(0.99).verticalDrag(0.99)
                .speed(1.5).spread(0.0075) // momentumHorizontal/Vertical default 0 (1.8 folds no shooter momentum)
                .spawnOffsetVertical(-0.1).spawnOffsetSideways(0.16)
                // 5-tick collision grace, then self-hits land (vanilla has no self-immunity). The pearl overrides with PASS_THROUGH.
                .shooterImmunityTicks(5)
                // entity-hit margin: grow the target bbox 0.3 per side and ray-test the path; too tight = arrows the 1.8 client predicts as hits fly past.
                .entityHitGrow(0.3)
                .syncInterval(20)
                // knockback pushes from the thrower (not the projectile); melee=false so no sprint extra applies.
                .knockback(kb())
                .knockbackSource(ProjectileTypeConfig.KnockbackSource.SHOOTER)
                // 0 damage, but routed through the damage system so the hurt flash + invul gate fire.
                .damage(0.0).damageType(ProjectileDamage.INSTANCE)
                .removeOnEntityHit(true).removeOnBlockHit(true)
                // throwables destroy (break + effect) on any rejected hit; the arrow overrides to DEFLECT/PASS_THROUGH.
                .invulnHit(ProjectileTypeConfig.HitResponse.DESTROY)
                .build();
    }

    /**
     * Vanilla 1.8 ender pearl overrides (on {@link #projectileDefaults()}): {@code selfHit(PASS_THROUGH)} - the 1.8
     * pearl ignores its own thrower and passes through (unlike snowball/egg, which can self-hit after the immunity
     * window). The teleport + 5 fall damage live in {@code PearlEntity}.
     */
    public static ProjectileTypeConfig pearl() {
        return ProjectileTypeConfig.builder(Pearl.KEY)
                .selfHit(ProjectileTypeConfig.HitResponse.PASS_THROUGH)
                .build();
    }

    /**
     * Vanilla 1.8 arrow overrides (on {@link #projectileDefaults()}): faster + heavier than a throwable (speed
     * {@code 3.0}, gravity {@code 0.05}), velocity-based damage ({@code damage = 2.0} per-speed multiplier), and it
     * sticks in blocks instead of breaking ({@code removeOnBlockHit = false}). Knockback stays shooter-relative
     * (inherited, not overridden to {@code PROJECTILE}): a plain arrow knocks the victim away from the shooter, not
     * along the arrow's flight. Punch rides as the extra-knockback level, scaling {@link #kb()}'s {@code extra}* knobs in
     * that same shooter-relative direction (vanilla's Punch is along the arrow's motion - close while it flies toward the
     * victim, exact only if re-sourced to {@code PROJECTILE}). Damage routes through {@link ProjectileDamage} for now (a
     * dedicated {@code minecraft:arrow} type is the follow-up).
     */
    public static ProjectileTypeConfig arrow() {
        return ProjectileTypeConfig.builder(Arrow.KEY)
                .gravity(0.05).speed(3.0)
                .damage(2.0).damageType(ProjectileDamage.INSTANCE)
                .removeOnEntityHit(true).removeOnBlockHit(false)
                .invulnHit(ProjectileTypeConfig.HitResponse.DEFLECT, ProjectileTypeConfig.HitResponse.PASS_THROUGH)
                .build();
    }

    /** Returns an AttackConfig with vanilla 1.8 behavior. */
    public static AttackConfig atk() {
        return AttackConfig.builder()
                .enabled(true)
                .ruleset(legacyAttack())
                .criticalRule(AttackEvent.CriticalRule.vanilla())
                .build();
    }

    /**
     * Vanilla 1.8 attribute config: the LEGACY source variants (Strength, Sharpness) plus the LEGACY defense stages
     * (armor linear, EPF randomized) the {@code AttributeSystem.mitigate} pipeline runs. A MODERN preset registers the
     * {@code .MODERN} variants + MODERN formulas instead - the version lives in what's registered, not a flag.
     */
    public static AttributeConfig attributes() {
        return AttributeConfig.builder()
                .sources(Strength.LEGACY, Weakness.LEGACY, Sharpness.LEGACY, Absorption.LEGACY) // 1.8 version variants
                .sources(VanillaAttributes.enchants())
                .sources(VanillaAttributes.effects())
                .armor(ArmorConfig.builder().formula(ArmorConfig.Formula.LEGACY_LINEAR).build())
                .protection(ProtectionConfig.builder().formula(ProtectionConfig.Formula.LEGACY_RANDOMIZED).build())
                .build();
    }

    /**
     * Vanilla 1.8 consumables: the golden apples, with 1.8-source effects (the registered
     * {@link VanillaConsumables#types() types} get their behavior here). Regular = Regen II (5s) + Absorption I (2m);
     * enchanted ("notch") = Regen V (30s) + Resistance (5m) + Fire Resistance (5m) + Absorption I (2m). Both restore
     * 4 food + 9.6 saturation. Differs from {@code Vanilla.consumables()} (26: enchanted is Regen II + Absorption IV).
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
                                        VanillaConsumables.eff(PotionEffect.REGENERATION, 5, 600),
                                        VanillaConsumables.eff(PotionEffect.RESISTANCE, 1, 6000),
                                        VanillaConsumables.eff(PotionEffect.FIRE_RESISTANCE, 1, 6000),
                                        VanillaConsumables.eff(PotionEffect.ABSORPTION, 1, 2400)))
                                .build())
                .build();
    }

    /**
     * Vanilla 1.8 blocking: sword block on the {@link VanillaBlocking#SWORDS sword materials}. Reduces a blocked hit to
     * {@code (1 + f) * 0.5} (vanilla {@code EntityHuman.damageEntity}, pre-armor) via the {@code SWORD} behavior
     * ({@code base -0.5, factor 0.5}); omnidirectional, no server-side movement slowdown (client-predicted), and only
     * non-armor-bypassing damage (the {@code SWORD} behavior reads the type's {@code bypassArmor}). Assign per scope via
     * {@code MechanicsProfile.blocking}, or install as the fallback. A MODERN preset maps the shield to {@code SHIELD}.
     */
    public static BlockingConfig blocking() {
        return BlockingConfig.builder()
                .defaults(BlockingTypeConfig.builder()
                        .behavior(BlockingBehavior.SWORD)
                        .reductionBase(-0.5).reductionFactor(0.5)
                        .build())
                .materials(VanillaBlocking.SWORDS)
                .build();
    }

    /** Vanilla 1.8 item registry: the LEGACY weapon table; armor rides Minestom's {@code ARMOR} attribute. */
    public static ItemRegistry items() {
        return new ItemRegistry(ItemDef.Version.LEGACY, VanillaItems.weapons());
    }

    /** Returns a DamageConfig with vanilla 1.8 values. */
    public static DamageConfig dmg() {
        return DamageConfig.builder()
                .invulTicks(10)
                .enableOverdamage(true)
                .syncHurtVelocity(true)
                .hurtKnockback(hurtKb())
                .typeConfigs(
                        fallDamage(),
                        inFireDamage(),
                        lavaDamage(),
                        burningDamage(),
                        cactusDamage(),
                        drownDamage(),
                        suffocationDamage(),
                        playerAttackDamage()
                )
                .build();
    }

    /** Vanilla 1.8 drowning: 2.0 at air {@code <= -20}, air snaps to max instantly out of water ({@code AirRefill.LEGACY}). */
    private static BreathingConfig drownDamage() {
        return BreathingConfig.builder()
                .key(DrowningDamage.KEY)
                .baseAmount(2.0)
                .airRefill(BreathingConfig.AirRefill.LEGACY)
                .bypassArmor(true) // drowning ignores armor in 1.8 (DROWN.setIgnoreArmor) - also makes it unblockable
                .build();
    }

    /** Vanilla suffocation: 1.0 per tick while the head is in a solid block (same 1.8 + 26). */
    private static DamageTypeConfig suffocationDamage() {
        // STUCK (in_wall) ignores armor in 1.8 (setIgnoreArmor) - also makes it unblockable
        return DamageTypeConfig.builder(SuffocationDamage.KEY).baseAmount(1.0).bypassArmor(true).build();
    }

    private static FallDamageConfig fallDamage() {
        return FallDamageConfig.builder()
                .formula(FallDamageConfig.Formula.LEGACY_CEIL)
                .bypassArmor(true) // fall ignores armor in 1.8 (DamageSource.FALL); only Feather Falling (EPF) reduces it. Also unblockable.
                // 1.8 has no SAFE_FALL_DISTANCE attribute: Jump Boost subtracts (amp+1) blocks directly (EntityLiving.e)
                .threshold(ctx -> {
                    if (ctx.snap().target() instanceof LivingEntity le) {
                        int amp = le.getEffectLevel(PotionEffect.JUMP_BOOST);
                        if (amp >= 0) return 3.0 + (amp + 1);
                    }
                    return 3.0;
                })
                .build();
    }

    private static BurningConfig inFireDamage() {
        return BurningConfig.builder()
                .key(InFireDamage.KEY)
                .baseAmount(1.0)
                .igniteTicks(160)
                .igniteWarmupInvulMult(2)
                .contactIntervalTicks(1)
                .build();
    }

    private static BurningConfig lavaDamage() {
        return BurningConfig.builder()
                .key(LavaDamage.KEY)
                .baseAmount(4.0)
                .igniteTicks(300)
                .igniteWarmupInvulMult(2)
                .contactIntervalTicks(1)
                .build();
    }

    private static BurningConfig burningDamage() {
        return BurningConfig.builder()
                .key(BurningDamage.KEY)
                .baseAmount(1.0)
                .intervalTicks(20)
                .bypassArmor(true) // on_fire (burn tick) ignores armor in 1.8 (BURN.setIgnoreArmor); in_fire/lava don't. Also unblockable.
                .build();
    }

    private static DamageTypeConfig cactusDamage() {
        return DamageTypeConfig.builder(CactusDamage.KEY).baseAmount(1.0).build();
    }

    private static MeleeDamageConfig playerAttackDamage() {
        return MeleeDamageConfig.builder().build();
    }

    /**
     * Returns a KnockbackConfig with every vanilla 1.8 value set except {@code velocity}, which is deliberately unset
     * so the friction fold reads the scoped rule ({@code MechanicsProfile.velocity(...)}). TODO(modern): 1.20+ KB
     * differs - grounded-only vertical fold, vertical add = power (not 0.4), resistance scales instead of gating,
     * 0.003 clamp + apex micro-step.
     */
    public static KnockbackConfig kb() {
        return KnockbackConfig.builder()
                .sprintBuffer(0)
                .horizontal(0.4)
                .vertical(0.4)
                .extraHorizontal(0.5)
                .extraVertical(0.1)
                .verticalBounds(null, 0.4000000059604645)
                .yawWeight(0.0)
                .extraYawWeight(1.0)
                .pitchWeight(0.0)
                .extraPitchWeight(0.0)
                .heightDelta(0.0)
                .extraHeightDelta(0.0)
                .horizontalCombine(KnockbackConfig.DirectionMode.VECTOR_ADDITION)
                .verticalCombine(KnockbackConfig.DirectionMode.SCALAR)
                .frictionH(2.0)
                .frictionV(2.0)
                .frictionModeH(KnockbackConfig.FrictionMode.DIVISOR)
                .frictionModeV(KnockbackConfig.FrictionMode.DIVISOR)
                // velocity deliberately unset: the friction fold uses the scoped rule (MechanicsProfile.velocity).
                .quantizeVelocity(true)
                .build();
    }

    /**
     * Generic damage-tick "knockback" (fire, cactus, fall, ...): vanilla broadcasts the victim's server-tracked
     * velocity with no impulse, so this config is all zeroes with a 1:1 friction fold - the velocity rule is the
     * broadcast. Bounds are explicitly cleared (the melee {@code 0.4} vertical cap must not clip a jump's {@code 0.42}
     * seed). {@code DamageSystem} routes every fresh non-melee, non-drown hit through the KnockbackSystem with this
     * config while {@code DamageConfig.syncHurtVelocity} is on.
     */
    public static KnockbackConfig hurtKb() {
        return KnockbackConfig.builder()
                .sprintBuffer(0)
                .horizontal(0.0)
                .vertical(0.0)
                .extraHorizontal(0.0)
                .extraVertical(0.0)
                .horizontalBounds(KnockbackConfig.Bounds.of(null, null))
                .verticalBounds(KnockbackConfig.Bounds.of(null, null))
                .extraHorizontalBounds(KnockbackConfig.Bounds.of(null, null))
                .extraVerticalBounds(KnockbackConfig.Bounds.of(null, null))
                .yawWeight(0.0)
                .extraYawWeight(0.0)
                .pitchWeight(0.0)
                .extraPitchWeight(0.0)
                .heightDelta(0.0)
                .extraHeightDelta(0.0)
                .horizontalCombine(KnockbackConfig.DirectionMode.VECTOR_ADDITION)
                .verticalCombine(KnockbackConfig.DirectionMode.SCALAR)
                .frictionH(1.0)
                .frictionV(1.0)
                .frictionModeH(KnockbackConfig.FrictionMode.FACTOR)
                .frictionModeV(KnockbackConfig.FrictionMode.FACTOR)
                // velocity deliberately unset: the broadcast folds the scoped rule (MechanicsProfile.velocity).
                .quantizeVelocity(true)
                .build();
    }

    /** Returns a PlayerConfig with vanilla 1.8 values: position broadcast every 2 ticks. */
    public static PlayerConfig player() {
        return PlayerConfig.builder()
                .positionBroadcastInterval(2)
                .build();
    }

    /** Legacy (pre-1.9) attack ruleset: applies melee damage, knockback, and resets attacker sprint. */
    public static AttackEvent.AttackRule.Ruleset legacyAttack() {
        return LegacyAttack::new;
    }

    /** Handles legacy (pre-1.9) combat attacks (damage, knockback, sprint reset). */
    private record LegacyAttack(Services services) implements AttackEvent.AttackRule {
        @Override public void processAttack(AttackEvent event) {
            // 1. Damage
            DamageSystem.DamageOutcome result = DamageSystem.DamageOutcome.FRESH_DAMAGE; // no damage system -> nothing absorbs the hit
            DamageSystem dmg = services.damage();
            if (dmg != null && event.target() != null) {
                DamageSnapshot snap = MeleeDamage.INSTANCE.snapshot(
                        event.attacker(), event.target(), event.critical(), event.item(), services);
                result = dmg.apply(snap);
            }
            // 2. Knockback - the Knockback enchant feeds the extra-knockback level (read off the weapon actually used,
            //    frozen for buffered hits; +1 for a sprint hit is added in the calculator).
            KnockbackSystem kb = services.knockback();
            if (result == DamageSystem.DamageOutcome.FRESH_DAMAGE && kb != null) {
                int extra = Enchants.level(event.item(), Knockback.KEY);
                var kbSnap = new KnockbackSnapshot(event.target(), true, event.attacker(), null, null, null, extra);
                kb.apply(kbSnap);
            }
            // 3. Attacker self-effects on a landed sprint/enchant hit: scale the attacker's own horizontal velocity
            //    by fullHitScale (vanilla 0.6, velocity-only) and clear sprint. A non-sprinting hit does neither.
            if (result.landed() && event.attacker() instanceof LivingEntity le) {
                double scale = event.resolvedConfig().fullHitScale();
                if (scale != 1.0 && sprintingForKb(le)) MotionTracker.scaleHorizontalResidual(le, scale);
                if (le instanceof OptimizedPlayer op) {
                    op.suppressSelf(() -> op.setSprinting(false));
                } else {
                    le.setSprinting(false);
                }
            }
        }

        /** Whether the attacker had the sprint knockback bonus (vanilla's {@code i > 0} gate); enchant levels fold in here once supported. */
        private boolean sprintingForKb(LivingEntity le) {
            if (!(le instanceof Player p)) return false;
            return services.sprintTracker() != null
                    ? SprintTracker.wasRecentlySprinting(services.sprintTracker(), p, 0)
                    : p.isSprinting();
        }
    }
}
