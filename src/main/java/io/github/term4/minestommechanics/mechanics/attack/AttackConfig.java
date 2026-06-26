package io.github.term4.minestommechanics.mechanics.attack;

import io.github.term4.minestommechanics.mechanics.vanilla18.Attack;
import io.github.term4.minestommechanics.api.event.AttackEvent;
import io.github.term4.minestommechanics.config.Config;
import io.github.term4.minestommechanics.config.FieldValue;
import io.github.term4.minestommechanics.mechanics.attack.AttackConfigResolver.AttackContext;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

/**
 * Immutable attack config. Use {@link #builder()}, {@link #toBuilder()}. Deliberately minimal - the {@link #ruleset}
 * processor owns the attack behavior, so the config only selects it, gates the pipeline, and picks the crit rule.
 */
public final class AttackConfig extends Config<AttackContext, AttackConfig> {

    public final FieldValue<AttackContext, Boolean> enabled;
    public final FieldValue<AttackContext, AttackEvent.AttackRule.Ruleset> ruleset;
    public final AttackEvent.CriticalRule criticalRule;
    /**
     * Attacker self-slowdown applied to the attacker's own tracked horizontal velocity on a landed sprint/enchant hit
     * (vanilla {@code motX/motZ *= 0.6}). Affects only their next knockback's friction fold, never the damage/KB dealt.
     * {@code 1.0} = none; vanilla {@code 0.6}.
     */
    public final FieldValue<AttackContext, Double> fullHitScale;

    private AttackConfig(Builder b) {
        super(b.subConfig);
        enabled = b.enabled;
        ruleset = b.ruleset;
        criticalRule = b.criticalRule;
        fullHitScale = b.fullHitScale;
    }

    /** Merges this config over base. */
    public AttackConfig fromBase(AttackConfig base) {
        return new Builder()
                .subConfig(subConfig != null ? subConfig : base.subConfig)
                .enabled(merge(enabled, base.enabled))
                .ruleset(merge(ruleset, base.ruleset))
                .criticalRule(criticalRule != null ? criticalRule : base.criticalRule)
                .fullHitScale(merge(fullHitScale, base.fullHitScale))
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
        private FieldValue<AttackContext, AttackEvent.AttackRule.Ruleset> ruleset;
        private AttackEvent.CriticalRule criticalRule;
        private FieldValue<AttackContext, Double> fullHitScale;

        Builder() {
            enabled = FieldValue.constant(true);
            ruleset = FieldValue.constant(Attack.ruleset());
            criticalRule = null;
            fullHitScale = FieldValue.constant(0.6); // vanilla 1.8 attacker self-slowdown
        }

        Builder(AttackConfig c) {
            subConfig = c.subConfig;
            enabled = c.enabled;
            ruleset = c.ruleset;
            criticalRule = c.criticalRule;
            fullHitScale = c.fullHitScale;
        }

        public Builder subConfig(Function<AttackContext, AttackConfig> fn) { subConfig = fn; return this; }

        public Builder enabled(Boolean v) { enabled = FieldValue.constant(v); return this; }
        public Builder enabled(Function<AttackContext, Boolean> fn) { enabled = FieldValue.of(fn); return this; }
        public Builder enabled(Boolean fallback, Function<AttackContext, Boolean> fn) { enabled = FieldValue.ofWithFallback(fallback, fn); return this; }

        public Builder ruleset(AttackEvent.AttackRule.Ruleset v) { ruleset = FieldValue.constant(v); return this; }
        public Builder ruleset(Function<AttackContext, AttackEvent.AttackRule.Ruleset> fn) { ruleset = FieldValue.of(fn); return this; }
        public Builder ruleset(AttackEvent.AttackRule.Ruleset fallback, Function<AttackContext, AttackEvent.AttackRule.Ruleset> fn) { ruleset = FieldValue.ofWithFallback(fallback, fn); return this; }

        public Builder criticalRule(AttackEvent.CriticalRule v) { criticalRule = v; return this; }

        /** Attacker self-slowdown on a landed sprint/enchant hit (velocity-only; {@code 1.0} = none). See {@link AttackConfig#fullHitScale}. */
        public Builder fullHitScale(Double v) { fullHitScale = FieldValue.constant(v); return this; }
        public Builder fullHitScale(Function<AttackContext, Double> fn) { fullHitScale = FieldValue.of(fn); return this; }
        public Builder fullHitScale(Double fallback, Function<AttackContext, Double> fn) { fullHitScale = FieldValue.ofWithFallback(fallback, fn); return this; }

        Builder enabled(FieldValue<AttackContext, Boolean> v) { enabled = v; return this; }
        Builder ruleset(FieldValue<AttackContext, AttackEvent.AttackRule.Ruleset> v) { ruleset = v; return this; }
        Builder fullHitScale(FieldValue<AttackContext, Double> v) { fullHitScale = v; return this; }

        public AttackConfig build() { return new AttackConfig(this); }
    }
}
