package io.github.term4.minestommechanics.mechanics.damage.types.fire;

import io.github.term4.minestommechanics.api.event.DamageEvent;
import io.github.term4.minestommechanics.config.FieldValue;
import io.github.term4.minestommechanics.mechanics.damage.DamageConfigResolver.DamageContext;
import io.github.term4.minestommechanics.mechanics.damage.types.DamageTypeConfig;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

/**
 * Config for {@link FireDamage}. Adds the fire-tick scheduling options (how often a burning entity
 * takes damage and how long it burns) on top of the common {@link DamageTypeConfig} tunables, each as
 * a {@link FieldValue} so they may be constants or context-aware lambdas. Built externally and
 * attached via {@code DamageConfig.builder().typeConfigs(...)}.
 */
public final class FireDamageConfig extends DamageTypeConfig {

    private final @Nullable FieldValue<DamageContext, Integer> intervalTicks;
    private final @Nullable FieldValue<DamageContext, Integer> durationTicks;

    private FireDamageConfig(Builder b) {
        super(b.common);
        this.intervalTicks = b.intervalTicks;
        this.durationTicks = b.durationTicks;
    }

    /** Ticks between successive fire-damage applications while burning (vanilla = 20). */
    public @Nullable Integer intervalTicks(DamageContext ctx) { return resolve(intervalTicks, ctx); }

    /** Total ticks a burn lasts when ignited (vanilla on fire = ~160). */
    public @Nullable Integer durationTicks(DamageContext ctx) { return resolve(durationTicks, ctx); }

    @Override
    public DamageTypeConfig fromBase(DamageTypeConfig base) {
        DamageTypeConfig mergedCommon = super.fromBase(base);
        Builder b = new Builder();
        b.common.copyFrom(mergedCommon);
        if (base instanceof FireDamageConfig f) {
            b.intervalTicks = mergeFv(this.intervalTicks, f.intervalTicks);
            b.durationTicks = mergeFv(this.durationTicks, f.durationTicks);
        } else {
            b.intervalTicks = this.intervalTicks;
            b.durationTicks = this.durationTicks;
        }
        return b.build();
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        // Vanilla baselines so a partial override keeps sensible values; invul/overdamage stay unset to inherit global.
        private final DamageTypeConfig.Builder common = new DamageTypeConfig.Builder().key(FireDamage.KEY).baseAmount(1.0);
        private FieldValue<DamageContext, Integer> intervalTicks = FieldValue.constant(20);
        private FieldValue<DamageContext, Integer> durationTicks = FieldValue.constant(160);

        public Builder baseAmount(Double v) { common.baseAmount(v); return this; }
        public Builder baseAmount(Function<DamageContext, Double> fn) { common.baseAmount(fn); return this; }
        public Builder baseAmount(Double fallback, Function<DamageContext, Double> fn) { common.baseAmount(fallback, fn); return this; }
        public Builder invulTicks(Integer v) { common.invulTicks(v); return this; }
        public Builder invulTicks(Function<DamageContext, Integer> fn) { common.invulTicks(fn); return this; }
        public Builder invulTicks(Integer fallback, Function<DamageContext, Integer> fn) { common.invulTicks(fallback, fn); return this; }
        public Builder overdamage(Boolean v) { common.overdamage(v); return this; }
        public Builder overdamage(Function<DamageContext, Boolean> fn) { common.overdamage(fn); return this; }
        public Builder overdamage(Boolean fallback, Function<DamageContext, Boolean> fn) { common.overdamage(fallback, fn); return this; }
        public Builder overdamageRule(DamageEvent.OverdamageRule v) { common.overdamageRule(v); return this; }
        public Builder overdamageRule(Function<DamageContext, DamageEvent.OverdamageRule> fn) { common.overdamageRule(fn); return this; }
        public Builder overdamageRule(DamageEvent.OverdamageRule fallback, Function<DamageContext, DamageEvent.OverdamageRule> fn) { common.overdamageRule(fallback, fn); return this; }
        public Builder silent(Boolean v) { common.silent(v); return this; }
        public Builder silent(Function<DamageContext, Boolean> fn) { common.silent(fn); return this; }
        public Builder silent(Boolean fallback, Function<DamageContext, Boolean> fn) { common.silent(fallback, fn); return this; }
        public Builder overdamageSilent(Boolean v) { common.overdamageSilent(v); return this; }
        public Builder overdamageSilent(Function<DamageContext, Boolean> fn) { common.overdamageSilent(fn); return this; }
        public Builder overdamageSilent(Boolean fallback, Function<DamageContext, Boolean> fn) { common.overdamageSilent(fallback, fn); return this; }
        public Builder triggersInvul(Boolean v) { common.triggersInvul(v); return this; }
        public Builder triggersInvul(Function<DamageContext, Boolean> fn) { common.triggersInvul(fn); return this; }
        public Builder bypassInvul(Boolean v) { common.bypassInvul(v); return this; }
        public Builder bypassInvul(Function<DamageContext, Boolean> fn) { common.bypassInvul(fn); return this; }
        public Builder subConfig(Function<DamageContext, DamageTypeConfig> fn) { common.subConfig(fn); return this; }

        public Builder intervalTicks(Integer v) { intervalTicks = FieldValue.constant(v); return this; }
        public Builder intervalTicks(Function<DamageContext, Integer> fn) { intervalTicks = FieldValue.of(fn); return this; }
        public Builder durationTicks(Integer v) { durationTicks = FieldValue.constant(v); return this; }
        public Builder durationTicks(Function<DamageContext, Integer> fn) { durationTicks = FieldValue.of(fn); return this; }

        public FireDamageConfig build() { return new FireDamageConfig(this); }
    }
}
