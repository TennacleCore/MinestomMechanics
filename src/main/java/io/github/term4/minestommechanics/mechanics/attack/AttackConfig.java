package io.github.term4.minestommechanics.mechanics.attack;

import io.github.term4.minestommechanics.Vanilla18;
import io.github.term4.minestommechanics.api.event.AttackEvent;
import io.github.term4.minestommechanics.config.Config;
import io.github.term4.minestommechanics.config.FieldValue;
import io.github.term4.minestommechanics.mechanics.attack.AttackConfigResolver.AttackContext;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

/** Immutable attack config. Use {@link #builder()}, {@link #toBuilder()}. */
public final class AttackConfig extends Config<AttackContext, AttackConfig> {

    /** Which invulnerability window the hit-queue buffer measures against. */
    public enum HitQueueInvulSource {
        /** The attack-invul window ({@code atkInvulnTicks}). */
        ATTACK,
        /** The damage-invul window. */
        DAMAGE,
        /** The knockback-invul window. */
        KNOCKBACK,
        /** Attack-invul window when {@code atkInvulnTicks > 0}, otherwise the damage window. */ //TODO: Maybe update / remove this in the future
        AUTO
    }

    public final FieldValue<AttackContext, Boolean> enabled;
    public final FieldValue<AttackContext, Integer> idleTimeout;
    public final FieldValue<AttackContext, Integer> atkInvulnTicks;
    public final FieldValue<AttackContext, Integer> sprintBuffer;
    public final FieldValue<AttackContext, Integer> hitQueueBuffer;
    public final FieldValue<AttackContext, Boolean> packetHits;
    public final FieldValue<AttackContext, Boolean> swingHits;
    public final FieldValue<AttackContext, Double> packetReach;
    public final FieldValue<AttackContext, Double> swingReach;
    public final FieldValue<AttackContext, Double> packetPadding;
    public final FieldValue<AttackContext, Double> swingPadding;
    public final FieldValue<AttackContext, AttackEvent.AttackRule.Ruleset> ruleset;
    public final AttackEvent.CriticalRule criticalRule;
    public final HitQueueInvulSource hitQueueInvulSource;

    private AttackConfig(Builder b) {
        super(b.subConfig);
        enabled = b.enabled;
        idleTimeout = b.idleTimeout;
        atkInvulnTicks = b.atkInvulnTicks;
        sprintBuffer = b.sprintBuffer;
        hitQueueBuffer = b.hitQueueBuffer;
        packetHits = b.packetHits;
        swingHits = b.swingHits;
        packetReach = b.packetReach;
        swingReach = b.swingReach;
        packetPadding = b.packetPadding;
        swingPadding = b.swingPadding;
        ruleset = b.ruleset;
        criticalRule = b.criticalRule;
        hitQueueInvulSource = b.hitQueueInvulSource;
    }

    /** Merges this config over base. */
    public AttackConfig fromBase(AttackConfig base) {
        return new Builder()
                .subConfig(subConfig != null ? subConfig : base.subConfig)
                .enabled(merge(enabled, base.enabled))
                .idleTimeout(merge(idleTimeout, base.idleTimeout))
                .atkInvulnTicks(merge(atkInvulnTicks, base.atkInvulnTicks))
                .sprintBuffer(merge(sprintBuffer, base.sprintBuffer))
                .hitQueueBuffer(merge(hitQueueBuffer, base.hitQueueBuffer))
                .packetHits(merge(packetHits, base.packetHits))
                .swingHits(merge(swingHits, base.swingHits))
                .packetReach(merge(packetReach, base.packetReach))
                .swingReach(merge(swingReach, base.swingReach))
                .packetPadding(merge(packetPadding, base.packetPadding))
                .swingPadding(merge(swingPadding, base.swingPadding))
                .ruleset(merge(ruleset, base.ruleset))
                .criticalRule(criticalRule != null ? criticalRule : base.criticalRule)
                .hitQueueInvulSource(hitQueueInvulSource != null ? hitQueueInvulSource : base.hitQueueInvulSource)
                .build();
    }

    public Builder toBuilder() { return new Builder(this); }

    public static Builder builder() { return builder(null); }
    public static Builder builder(@Nullable AttackConfig base) { return base != null ? new Builder(base) : new Builder(); }

    /** Returns a new config with default values. */
    public static AttackConfig defaultConfig() { return builder().build(); }

    public static final class Builder {
        private Function<AttackContext, AttackConfig> subConfig;
        private FieldValue<AttackContext, Boolean> enabled;
        private FieldValue<AttackContext, Integer> idleTimeout;
        private FieldValue<AttackContext, Integer> atkInvulnTicks;
        private FieldValue<AttackContext, Integer> sprintBuffer;
        private FieldValue<AttackContext, Integer> hitQueueBuffer;
        private FieldValue<AttackContext, Boolean> packetHits;
        private FieldValue<AttackContext, Boolean> swingHits;
        private FieldValue<AttackContext, Double> packetReach;
        private FieldValue<AttackContext, Double> swingReach;
        private FieldValue<AttackContext, Double> packetPadding;
        private FieldValue<AttackContext, Double> swingPadding;
        private FieldValue<AttackContext, AttackEvent.AttackRule.Ruleset> ruleset;
        private AttackEvent.CriticalRule criticalRule;
        private HitQueueInvulSource hitQueueInvulSource;

        Builder() {
            enabled = FieldValue.constant(true);
            idleTimeout = null;
            atkInvulnTicks = null;
            sprintBuffer = null;
            hitQueueBuffer = FieldValue.constant(0);
            packetHits = FieldValue.constant(true);
            swingHits = FieldValue.constant(false);
            packetReach = FieldValue.constant(10.0);
            swingReach = FieldValue.constant(3.0);
            packetPadding = FieldValue.constant(2.0);
            swingPadding = FieldValue.constant(0.0);
            ruleset = FieldValue.constant(Vanilla18.legacyAttack());
            criticalRule = null;
            hitQueueInvulSource = HitQueueInvulSource.AUTO;
        }

        Builder(AttackConfig c) {
            subConfig = c.subConfig;
            enabled = c.enabled;
            idleTimeout = c.idleTimeout;
            atkInvulnTicks = c.atkInvulnTicks;
            sprintBuffer = c.sprintBuffer;
            hitQueueBuffer = c.hitQueueBuffer;
            packetHits = c.packetHits;
            swingHits = c.swingHits;
            packetReach = c.packetReach;
            swingReach = c.swingReach;
            packetPadding = c.packetPadding;
            swingPadding = c.swingPadding;
            ruleset = c.ruleset;
            criticalRule = c.criticalRule;
            hitQueueInvulSource = c.hitQueueInvulSource;
        }

        public Builder subConfig(Function<AttackContext, AttackConfig> fn) { subConfig = fn; return this; }

        public Builder enabled(Boolean v) { enabled = FieldValue.constant(v); return this; }
        public Builder enabled(Function<AttackContext, Boolean> fn) { enabled = FieldValue.of(fn); return this; }
        public Builder enabled(Boolean fallback, Function<AttackContext, Boolean> fn) { enabled = FieldValue.ofWithFallback(fallback, fn); return this; }

        public Builder idleTimeout(Integer v) { idleTimeout = FieldValue.constant(v); return this; }
        public Builder idleTimeout(Function<AttackContext, Integer> fn) { idleTimeout = FieldValue.of(fn); return this; }
        public Builder idleTimeout(Integer fallback, Function<AttackContext, Integer> fn) { idleTimeout = FieldValue.ofWithFallback(fallback, fn); return this; }

        public Builder atkInvulnTicks(Integer v) { atkInvulnTicks = FieldValue.constant(v); return this; }
        public Builder atkInvulnTicks(Function<AttackContext, Integer> fn) { atkInvulnTicks = FieldValue.of(fn); return this; }
        public Builder atkInvulnTicks(Integer fallback, Function<AttackContext, Integer> fn) { atkInvulnTicks = FieldValue.ofWithFallback(fallback, fn); return this; }

        public Builder sprintBuffer(Integer v) { sprintBuffer = FieldValue.constant(v); return this; }
        public Builder sprintBuffer(Function<AttackContext, Integer> fn) { sprintBuffer = FieldValue.of(fn); return this; }
        public Builder sprintBuffer(Integer fallback, Function<AttackContext, Integer> fn) { sprintBuffer = FieldValue.ofWithFallback(fallback, fn); return this; }

        public Builder hitQueueBuffer(Integer v) { hitQueueBuffer = FieldValue.constant(v); return this; }
        public Builder hitQueueBuffer(Function<AttackContext, Integer> fn) { hitQueueBuffer = FieldValue.of(fn); return this; }
        public Builder hitQueueBuffer(Integer fallback, Function<AttackContext, Integer> fn) { hitQueueBuffer = FieldValue.ofWithFallback(fallback, fn); return this; }

        public Builder packetHits(Boolean v) { packetHits = FieldValue.constant(v); return this; }
        public Builder packetHits(Function<AttackContext, Boolean> fn) { packetHits = FieldValue.of(fn); return this; }
        public Builder packetHits(Boolean fallback, Function<AttackContext, Boolean> fn) { packetHits = FieldValue.ofWithFallback(fallback, fn); return this; }

        public Builder swingHits(Boolean v) { swingHits = FieldValue.constant(v); return this; }
        public Builder swingHits(Function<AttackContext, Boolean> fn) { swingHits = FieldValue.of(fn); return this; }
        public Builder swingHits(Boolean fallback, Function<AttackContext, Boolean> fn) { swingHits = FieldValue.ofWithFallback(fallback, fn); return this; }

        public Builder packetReach(Double v) { packetReach = FieldValue.constant(v); return this; }
        public Builder packetReach(Function<AttackContext, Double> fn) { packetReach = FieldValue.of(fn); return this; }
        public Builder packetReach(Double fallback, Function<AttackContext, Double> fn) { packetReach = FieldValue.ofWithFallback(fallback, fn); return this; }

        public Builder swingReach(Double v) { swingReach = FieldValue.constant(v); return this; }
        public Builder swingReach(Function<AttackContext, Double> fn) { swingReach = FieldValue.of(fn); return this; }
        public Builder swingReach(Double fallback, Function<AttackContext, Double> fn) { swingReach = FieldValue.ofWithFallback(fallback, fn); return this; }

        public Builder packetPadding(Double v) { packetPadding = FieldValue.constant(v); return this; }
        public Builder packetPadding(Function<AttackContext, Double> fn) { packetPadding = FieldValue.of(fn); return this; }
        public Builder packetPadding(Double fallback, Function<AttackContext, Double> fn) { packetPadding = FieldValue.ofWithFallback(fallback, fn); return this; }

        public Builder swingPadding(Double v) { swingPadding = FieldValue.constant(v); return this; }
        public Builder swingPadding(Function<AttackContext, Double> fn) { swingPadding = FieldValue.of(fn); return this; }
        public Builder swingPadding(Double fallback, Function<AttackContext, Double> fn) { swingPadding = FieldValue.ofWithFallback(fallback, fn); return this; }

        public Builder ruleset(AttackEvent.AttackRule.Ruleset v) { ruleset = FieldValue.constant(v); return this; }
        public Builder ruleset(Function<AttackContext, AttackEvent.AttackRule.Ruleset> fn) { ruleset = FieldValue.of(fn); return this; }
        public Builder ruleset(AttackEvent.AttackRule.Ruleset fallback, Function<AttackContext, AttackEvent.AttackRule.Ruleset> fn) { ruleset = FieldValue.ofWithFallback(fallback, fn); return this; }

        public Builder criticalRule(AttackEvent.CriticalRule v) { criticalRule = v; return this; }

        public Builder hitQueueInvulSource(HitQueueInvulSource v) { hitQueueInvulSource = v; return this; }

        Builder enabled(FieldValue<AttackContext, Boolean> v) { enabled = v; return this; }
        Builder idleTimeout(FieldValue<AttackContext, Integer> v) { idleTimeout = v; return this; }
        Builder atkInvulnTicks(FieldValue<AttackContext, Integer> v) { atkInvulnTicks = v; return this; }
        Builder sprintBuffer(FieldValue<AttackContext, Integer> v) { sprintBuffer = v; return this; }
        Builder hitQueueBuffer(FieldValue<AttackContext, Integer> v) { hitQueueBuffer = v; return this; }
        Builder packetHits(FieldValue<AttackContext, Boolean> v) { packetHits = v; return this; }
        Builder swingHits(FieldValue<AttackContext, Boolean> v) { swingHits = v; return this; }
        Builder packetReach(FieldValue<AttackContext, Double> v) { packetReach = v; return this; }
        Builder swingReach(FieldValue<AttackContext, Double> v) { swingReach = v; return this; }
        Builder packetPadding(FieldValue<AttackContext, Double> v) { packetPadding = v; return this; }
        Builder swingPadding(FieldValue<AttackContext, Double> v) { swingPadding = v; return this; }
        Builder ruleset(FieldValue<AttackContext, AttackEvent.AttackRule.Ruleset> v) { ruleset = v; return this; }

        public AttackConfig build() { return new AttackConfig(this); }
    }
}
