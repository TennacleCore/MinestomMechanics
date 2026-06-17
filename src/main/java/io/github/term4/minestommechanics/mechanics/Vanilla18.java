package io.github.term4.minestommechanics.mechanics;

import io.github.term4.minestommechanics.Services;
import io.github.term4.minestommechanics.platform.player.OptimizedPlayer;
import io.github.term4.minestommechanics.api.event.AttackEvent;
import io.github.term4.minestommechanics.mechanics.attack.AttackConfig;
import io.github.term4.minestommechanics.mechanics.damage.DamageSnapshot;
import io.github.term4.minestommechanics.mechanics.damage.DamageConfig;
import io.github.term4.minestommechanics.mechanics.damage.DamageSystem;
import io.github.term4.minestommechanics.mechanics.damage.types.DamageTypeConfig;
import io.github.term4.minestommechanics.mechanics.damage.types.burning.BurningConfig;
import io.github.term4.minestommechanics.mechanics.damage.types.burning.BurningDamage;
import io.github.term4.minestommechanics.mechanics.damage.types.burning.InFireDamage;
import io.github.term4.minestommechanics.mechanics.damage.types.burning.LavaDamage;
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
     * along the arrow's flight. (Punch adds a separate motion-direction knockback - TODO with Power/Punch/Flame.)
     * Damage routes through {@link ProjectileDamage} for now (a dedicated {@code minecraft:arrow} type is the follow-up).
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
                        playerAttackDamage()
                )
                .build();
    }

    private static FallDamageConfig fallDamage() {
        return FallDamageConfig.builder()
                .formula(FallDamageConfig.Formula.LEGACY_CEIL)
                .threshold(3.0)
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
            // 2. Knockback
            KnockbackSystem kb = services.knockback();
            if (result == DamageSystem.DamageOutcome.FRESH_DAMAGE && kb != null) {
                var kbSnap = new KnockbackSnapshot(event.target(), true, event.attacker(), null, null, null);
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
