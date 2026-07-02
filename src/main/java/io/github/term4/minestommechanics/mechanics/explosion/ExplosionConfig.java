package io.github.term4.minestommechanics.mechanics.explosion;

import io.github.term4.minestommechanics.config.Config;
import io.github.term4.minestommechanics.config.FieldValue;
import io.github.term4.minestommechanics.mechanics.attribute.defense.Bypass;
import io.github.term4.minestommechanics.mechanics.explosion.ExplosionConfigResolver.ExplosionContext;
import io.github.term4.minestommechanics.mechanics.knockback.KnockbackConfig;
import net.minestom.server.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Immutable per-explosion config (radius, damage curve, knockback, exposure). Mitigation/i-frames/death are the
 * {@code DamageSystem}'s, not configured here. Use {@link #builder()}, {@link #toBuilder()}.
 *
 * <p>Two knockback paths: {@link #baseKnockback} &gt; 0 = a radial base toward {@code feet+baseHeight} plus the push,
 * SET as one velocity (Hypixel); {@code 0} = the vanilla hurt-KB fold ({@link #damageKnockback}) plus the push.
 */
public final class ExplosionConfig extends Config<ExplosionContext, ExplosionConfig> {

    /** Default radius for the no-power {@code explode} overloads; an explicit call power wins. */
    public final FieldValue<ExplosionContext, Double> power;
    /** Damage-curve constant: 8.0 (1.8) or 7.0 (modern). */
    public final FieldValue<ExplosionContext, Double> damageConstant;
    /** Floor the per-entity damage to an int (1.8 parity). */
    public final FieldValue<ExplosionContext, Boolean> floorDamage;
    /** Flat damage to every in-range target, overriding the falloff curve (Hypixel/BedWars = 2.0). {@code null} = use the curve. */
    public final FieldValue<ExplosionContext, Double> flatDamage;
    /** Scale on the final damage, applied AFTER the floor (MineMen Fireball-Fight = the vanilla floored curve × 0.05). Default 1.0. */
    public final FieldValue<ExplosionContext, Double> damageScale;
    /** Mitigation the explosion damage skips (e.g. armor points only); {@code null} = normal mitigation. */
    public final @Nullable Bypass damageBypass;
    /** Scale on the radial falloff push ({@code impact · multiplier}); vanilla 1.0. */
    public final FieldValue<ExplosionContext, Double> knockbackMultiplier;
    /** Damage-knockback on a fresh hit (before the push); {@code null} = the vanilla 1.8 {@code a()}. Only used when {@link #baseKnockback} is 0. */
    public final @Nullable KnockbackConfig damageKnockback;
    /** Client-applied push on the explosion packet when the velocity path doesn't fire (default, vanilla); false = velocity-only, blocked hits get nothing (MineMen sends an all-zero packet). */
    public final FieldValue<ExplosionContext, Boolean> packetPush;
    /** Radial base magnitude, toward {@link #baseHeight} above the feet ({@link #baseHorizontalScale}/{@link #baseDownwardScale} shape the other axes). 0 = use {@link #damageKnockback}. */
    public final FieldValue<ExplosionContext, Double> baseKnockback;
    /** Height above the feet the radial {@link #baseKnockback} aims at; default 1.0. */
    public final FieldValue<ExplosionContext, Double> baseHeight;
    /** Sideways (X/Z) component as a multiple of {@link #baseKnockback}; 1.0 = isotropic (Hypixel 0.8). */
    public final FieldValue<ExplosionContext, Double> baseHorizontalScale;
    /** Downward (-Y) component as a multiple of {@link #baseKnockback}; 1.0 = isotropic (Hypixel 0.4). */
    public final FieldValue<ExplosionContext, Double> baseDownwardScale;
    /** Raytrace each entity's line-of-sight exposure (default on); off = full exposure for everyone in range. */
    public final FieldValue<ExplosionContext, Boolean> exposure;
    /** Hypixel KB gate: below this falloff {@link ExplosionCalculator#impact impact}, no explosion KB (only the projectile KB). {@code null} = no gate; Hypixel ≈ 0.435. */
    public final FieldValue<ExplosionContext, Double> knockbackImpactFloor;
    /** Carried on {@code ExplosionEvent} for a block/fire listener; the library itself never sets fire. */
    public final FieldValue<ExplosionContext, Boolean> fire;
    /** Whether the source entity is hit by its own explosion (vanilla excludes it). */
    public final FieldValue<ExplosionContext, Boolean> affectsSource;
    /** Which entities the explosion knocks back. {@code null} = players + non-living physics entities (TNT etc.); e.g. {@code e -> e instanceof Player} restores player-only. */
    public final @Nullable Predicate<Entity> knockbackTargets;
    /** Per-victim eye height for the push direction (living entities only); {@code null} = the standing registry eye (Hypixel pushes from 1.62 even sneaking; vanilla/MineMen use the sneak-aware head height). */
    public final @Nullable Function<Entity, Double> pushEye;

    private ExplosionConfig(Builder b) {
        super(b.subConfig);
        power = b.power;
        damageConstant = b.damageConstant;
        floorDamage = b.floorDamage;
        flatDamage = b.flatDamage;
        damageScale = b.damageScale;
        damageBypass = b.damageBypass;
        knockbackMultiplier = b.knockbackMultiplier;
        damageKnockback = b.damageKnockback;
        packetPush = b.packetPush;
        baseKnockback = b.baseKnockback;
        baseHeight = b.baseHeight;
        baseHorizontalScale = b.baseHorizontalScale;
        baseDownwardScale = b.baseDownwardScale;
        exposure = b.exposure;
        knockbackImpactFloor = b.knockbackImpactFloor;
        fire = b.fire;
        affectsSource = b.affectsSource;
        knockbackTargets = b.knockbackTargets;
        pushEye = b.pushEye;
    }

    /** Merges this config over base. */
    public ExplosionConfig fromBase(ExplosionConfig base) {
        return new Builder()
                .subConfig(subConfig != null ? subConfig : base.subConfig)
                .power(merge(power, base.power))
                .damageConstant(merge(damageConstant, base.damageConstant))
                .floorDamage(merge(floorDamage, base.floorDamage))
                .flatDamage(merge(flatDamage, base.flatDamage))
                .damageScale(merge(damageScale, base.damageScale))
                .damageBypass(damageBypass != null ? damageBypass : base.damageBypass)
                .knockbackMultiplier(merge(knockbackMultiplier, base.knockbackMultiplier))
                .damageKnockback(damageKnockback != null ? damageKnockback : base.damageKnockback)
                .packetPush(merge(packetPush, base.packetPush))
                .baseKnockback(merge(baseKnockback, base.baseKnockback))
                .baseHeight(merge(baseHeight, base.baseHeight))
                .baseHorizontalScale(merge(baseHorizontalScale, base.baseHorizontalScale))
                .baseDownwardScale(merge(baseDownwardScale, base.baseDownwardScale))
                .exposure(merge(exposure, base.exposure))
                .knockbackImpactFloor(merge(knockbackImpactFloor, base.knockbackImpactFloor))
                .fire(merge(fire, base.fire))
                .affectsSource(merge(affectsSource, base.affectsSource))
                .knockbackTargets(knockbackTargets != null ? knockbackTargets : base.knockbackTargets)
                .pushEye(pushEye != null ? pushEye : base.pushEye)
                .build();
    }

    public Builder toBuilder() { return new Builder(this); }

    public static Builder builder() { return new Builder(); }

    public static Builder builder(@Nullable ExplosionConfig base) {
        return base != null ? new Builder(base) : new Builder();
    }

    public static final class Builder {
        private Function<ExplosionContext, ExplosionConfig> subConfig;
        private FieldValue<ExplosionContext, Double> power;
        private FieldValue<ExplosionContext, Double> damageConstant;
        private FieldValue<ExplosionContext, Boolean> floorDamage;
        private FieldValue<ExplosionContext, Double> flatDamage;
        private FieldValue<ExplosionContext, Double> damageScale;
        private Bypass damageBypass;
        private FieldValue<ExplosionContext, Double> knockbackMultiplier;
        private KnockbackConfig damageKnockback;
        private FieldValue<ExplosionContext, Boolean> packetPush;
        private FieldValue<ExplosionContext, Double> baseKnockback;
        private FieldValue<ExplosionContext, Double> baseHeight;
        private FieldValue<ExplosionContext, Double> baseHorizontalScale;
        private FieldValue<ExplosionContext, Double> baseDownwardScale;
        private FieldValue<ExplosionContext, Boolean> exposure;
        private FieldValue<ExplosionContext, Double> knockbackImpactFloor;
        private FieldValue<ExplosionContext, Boolean> fire;
        private FieldValue<ExplosionContext, Boolean> affectsSource;
        private Predicate<Entity> knockbackTargets;
        private Function<Entity, Double> pushEye;

        Builder() {}

        Builder(ExplosionConfig c) {
            subConfig = c.subConfig;
            power = c.power;
            damageConstant = c.damageConstant;
            floorDamage = c.floorDamage;
            flatDamage = c.flatDamage;
            damageScale = c.damageScale;
            damageBypass = c.damageBypass;
            knockbackMultiplier = c.knockbackMultiplier;
            damageKnockback = c.damageKnockback;
            packetPush = c.packetPush;
            baseKnockback = c.baseKnockback;
            baseHeight = c.baseHeight;
            baseHorizontalScale = c.baseHorizontalScale;
            baseDownwardScale = c.baseDownwardScale;
            exposure = c.exposure;
            knockbackImpactFloor = c.knockbackImpactFloor;
            fire = c.fire;
            affectsSource = c.affectsSource;
            knockbackTargets = c.knockbackTargets;
            pushEye = c.pushEye;
        }

        public Builder subConfig(Function<ExplosionContext, ExplosionConfig> fn) { subConfig = fn; return this; }
        public Builder power(Double v) { power = FieldValue.constant(v); return this; }
        public Builder power(Function<ExplosionContext, Double> fn) { power = FieldValue.of(fn); return this; }
        public Builder power(Double fallback, Function<ExplosionContext, Double> fn) { power = FieldValue.ofWithFallback(fallback, fn); return this; }
        public Builder damageConstant(Double v) { damageConstant = FieldValue.constant(v); return this; }
        public Builder damageConstant(Function<ExplosionContext, Double> fn) { damageConstant = FieldValue.of(fn); return this; }
        public Builder damageConstant(Double fallback, Function<ExplosionContext, Double> fn) { damageConstant = FieldValue.ofWithFallback(fallback, fn); return this; }
        public Builder floorDamage(Boolean v) { floorDamage = FieldValue.constant(v); return this; }
        public Builder floorDamage(Function<ExplosionContext, Boolean> fn) { floorDamage = FieldValue.of(fn); return this; }
        public Builder floorDamage(Boolean fallback, Function<ExplosionContext, Boolean> fn) { floorDamage = FieldValue.ofWithFallback(fallback, fn); return this; }
        public Builder flatDamage(Double v) { flatDamage = FieldValue.constant(v); return this; }
        public Builder flatDamage(Function<ExplosionContext, Double> fn) { flatDamage = FieldValue.of(fn); return this; }
        public Builder flatDamage(Double fallback, Function<ExplosionContext, Double> fn) { flatDamage = FieldValue.ofWithFallback(fallback, fn); return this; }
        public Builder damageScale(Double v) { damageScale = FieldValue.constant(v); return this; }
        public Builder damageScale(Function<ExplosionContext, Double> fn) { damageScale = FieldValue.of(fn); return this; }
        public Builder damageScale(Double fallback, Function<ExplosionContext, Double> fn) { damageScale = FieldValue.ofWithFallback(fallback, fn); return this; }
        public Builder damageBypass(Bypass v) { damageBypass = v; return this; }
        public Builder knockbackMultiplier(Double v) { knockbackMultiplier = FieldValue.constant(v); return this; }
        public Builder knockbackMultiplier(Function<ExplosionContext, Double> fn) { knockbackMultiplier = FieldValue.of(fn); return this; }
        public Builder knockbackMultiplier(Double fallback, Function<ExplosionContext, Double> fn) { knockbackMultiplier = FieldValue.ofWithFallback(fallback, fn); return this; }
        public Builder damageKnockback(KnockbackConfig v) { damageKnockback = v; return this; }
        public Builder packetPush(Boolean v) { packetPush = FieldValue.constant(v); return this; }
        public Builder packetPush(Function<ExplosionContext, Boolean> fn) { packetPush = FieldValue.of(fn); return this; }
        public Builder packetPush(Boolean fallback, Function<ExplosionContext, Boolean> fn) { packetPush = FieldValue.ofWithFallback(fallback, fn); return this; }
        public Builder baseKnockback(Double v) { baseKnockback = FieldValue.constant(v); return this; }
        public Builder baseKnockback(Function<ExplosionContext, Double> fn) { baseKnockback = FieldValue.of(fn); return this; }
        public Builder baseKnockback(Double fallback, Function<ExplosionContext, Double> fn) { baseKnockback = FieldValue.ofWithFallback(fallback, fn); return this; }
        public Builder baseHeight(Double v) { baseHeight = FieldValue.constant(v); return this; }
        public Builder baseHeight(Function<ExplosionContext, Double> fn) { baseHeight = FieldValue.of(fn); return this; }
        public Builder baseHeight(Double fallback, Function<ExplosionContext, Double> fn) { baseHeight = FieldValue.ofWithFallback(fallback, fn); return this; }
        public Builder baseHorizontalScale(Double v) { baseHorizontalScale = FieldValue.constant(v); return this; }
        public Builder baseHorizontalScale(Function<ExplosionContext, Double> fn) { baseHorizontalScale = FieldValue.of(fn); return this; }
        public Builder baseHorizontalScale(Double fallback, Function<ExplosionContext, Double> fn) { baseHorizontalScale = FieldValue.ofWithFallback(fallback, fn); return this; }
        public Builder baseDownwardScale(Double v) { baseDownwardScale = FieldValue.constant(v); return this; }
        public Builder baseDownwardScale(Function<ExplosionContext, Double> fn) { baseDownwardScale = FieldValue.of(fn); return this; }
        public Builder baseDownwardScale(Double fallback, Function<ExplosionContext, Double> fn) { baseDownwardScale = FieldValue.ofWithFallback(fallback, fn); return this; }
        Builder baseKnockback(FieldValue<ExplosionContext, Double> v) { baseKnockback = v; return this; }
        Builder baseHeight(FieldValue<ExplosionContext, Double> v) { baseHeight = v; return this; }
        Builder baseHorizontalScale(FieldValue<ExplosionContext, Double> v) { baseHorizontalScale = v; return this; }
        Builder baseDownwardScale(FieldValue<ExplosionContext, Double> v) { baseDownwardScale = v; return this; }
        public Builder exposure(Boolean v) { exposure = FieldValue.constant(v); return this; }
        public Builder exposure(Function<ExplosionContext, Boolean> fn) { exposure = FieldValue.of(fn); return this; }
        public Builder exposure(Boolean fallback, Function<ExplosionContext, Boolean> fn) { exposure = FieldValue.ofWithFallback(fallback, fn); return this; }
        public Builder knockbackImpactFloor(Double v) { knockbackImpactFloor = FieldValue.constant(v); return this; }
        public Builder knockbackImpactFloor(Function<ExplosionContext, Double> fn) { knockbackImpactFloor = FieldValue.of(fn); return this; }
        public Builder knockbackImpactFloor(Double fallback, Function<ExplosionContext, Double> fn) { knockbackImpactFloor = FieldValue.ofWithFallback(fallback, fn); return this; }
        public Builder fire(Boolean v) { fire = FieldValue.constant(v); return this; }
        public Builder fire(Function<ExplosionContext, Boolean> fn) { fire = FieldValue.of(fn); return this; }
        public Builder fire(Boolean fallback, Function<ExplosionContext, Boolean> fn) { fire = FieldValue.ofWithFallback(fallback, fn); return this; }
        public Builder affectsSource(Boolean v) { affectsSource = FieldValue.constant(v); return this; }
        public Builder affectsSource(Function<ExplosionContext, Boolean> fn) { affectsSource = FieldValue.of(fn); return this; }
        public Builder affectsSource(Boolean fallback, Function<ExplosionContext, Boolean> fn) { affectsSource = FieldValue.ofWithFallback(fallback, fn); return this; }
        public Builder knockbackTargets(Predicate<Entity> v) { knockbackTargets = v; return this; }
        public Builder pushEye(Function<Entity, Double> v) { pushEye = v; return this; }

        Builder packetPush(FieldValue<ExplosionContext, Boolean> v) { packetPush = v; return this; }
        Builder power(FieldValue<ExplosionContext, Double> v) { power = v; return this; }
        Builder damageConstant(FieldValue<ExplosionContext, Double> v) { damageConstant = v; return this; }
        Builder floorDamage(FieldValue<ExplosionContext, Boolean> v) { floorDamage = v; return this; }
        Builder flatDamage(FieldValue<ExplosionContext, Double> v) { flatDamage = v; return this; }
        Builder damageScale(FieldValue<ExplosionContext, Double> v) { damageScale = v; return this; }
        Builder knockbackMultiplier(FieldValue<ExplosionContext, Double> v) { knockbackMultiplier = v; return this; }
        Builder exposure(FieldValue<ExplosionContext, Boolean> v) { exposure = v; return this; }
        Builder knockbackImpactFloor(FieldValue<ExplosionContext, Double> v) { knockbackImpactFloor = v; return this; }
        Builder fire(FieldValue<ExplosionContext, Boolean> v) { fire = v; return this; }
        Builder affectsSource(FieldValue<ExplosionContext, Boolean> v) { affectsSource = v; return this; }

        public ExplosionConfig build() { return new ExplosionConfig(this); }
    }
}
