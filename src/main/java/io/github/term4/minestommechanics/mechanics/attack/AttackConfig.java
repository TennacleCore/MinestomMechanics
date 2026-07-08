package io.github.term4.minestommechanics.mechanics.attack;

import io.github.term4.minestommechanics.codegen.GenerateBuilder;
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
@GenerateBuilder
public final class AttackConfig extends Config<AttackContext, AttackConfig> {

    /** Vanilla 1.8 attacker self-slowdown on a landed sprint/enchant hit ({@code motX/motZ *= 0.6}). */
    public static final double VANILLA_FULL_HIT_SCALE = 0.6;

    public final FieldValue<AttackContext, Boolean> enabled;
    public final FieldValue<AttackContext, AttackEvent.AttackRule.Ruleset> ruleset;
    public final AttackEvent.CriticalRule criticalRule;
    /**
     * Attacker self-slowdown applied to the attacker's own tracked horizontal velocity on a landed sprint/enchant hit
     * (vanilla {@code motX/motZ *= 0.6}). Affects only their next knockback's friction fold, never the damage/KB dealt.
     * {@code 1.0} = none; vanilla {@link #VANILLA_FULL_HIT_SCALE}.
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
        Builder b = new Builder();
        b.mergeKnobs(this, base);
        return b
                .subConfig(subConfig != null ? subConfig : base.subConfig)
                .criticalRule(criticalRule != null ? criticalRule : base.criticalRule)
                .build();
    }

    public Builder toBuilder() { return new Builder(this); }

    public static Builder builder() { return builder(null); }
    public static Builder builder(@Nullable AttackConfig base) { return base != null ? new Builder(base) : new Builder(); }

    /** Returns a new config with default values. */
    public static AttackConfig defaultConfig() { return builder().build(); }

    public static final class Builder extends AttackConfigBuilderBase<Builder> {

        @Override protected Builder self() { return this; }
        private Function<AttackContext, AttackConfig> subConfig;
        private AttackEvent.CriticalRule criticalRule;

        Builder() {
            enabled = FieldValue.constant(true);
            ruleset = FieldValue.constant(Attack.ruleset());
            criticalRule = null;
            fullHitScale = FieldValue.constant(VANILLA_FULL_HIT_SCALE);
        }

        Builder(AttackConfig c) {
            super(c);
            subConfig = c.subConfig;
            criticalRule = c.criticalRule;
        }

        public Builder subConfig(Function<AttackContext, AttackConfig> fn) { subConfig = fn; return this; }

        public Builder criticalRule(AttackEvent.CriticalRule v) { criticalRule = v; return this; }

        public AttackConfig build() { return new AttackConfig(this); }
    }
}
