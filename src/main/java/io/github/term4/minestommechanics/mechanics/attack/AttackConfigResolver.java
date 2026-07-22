package io.github.term4.minestommechanics.mechanics.attack;

import io.github.term4.minestommechanics.Services;
import io.github.term4.minestommechanics.presets.vanilla18.Attack;
import io.github.term4.minestommechanics.api.event.attack.AttackEvent;
import io.github.term4.minestommechanics.config.FieldValue;
import org.jetbrains.annotations.Nullable;

/** Resolves AttackConfig with context into plain values. */
public final class AttackConfigResolver {

    private AttackConfigResolver() {}

    public record AttackContext(AttackSnapshot snap, Services services) {
        public static AttackContext of(AttackSnapshot snap, Services services) {
            return new AttackContext(snap, services);
        }
    }

    public static ResolvedAttackConfig resolve(AttackConfig config, AttackContext ctx) {
        if (config == null) return ResolvedAttackConfig.defaults();

        AttackConfig cfg = config;
        if (cfg.subConfig != null) {
            AttackConfig sub = cfg.subConfig.apply(ctx);
            if (sub != null) cfg = sub.fromBase(cfg);
        }

        // no attack-level invul/buffering: the damage/knockback systems gate themselves
        Boolean enabledVal = resolve(cfg.enabled, ctx);
        AttackEvent.AttackRule.Ruleset rulesetVal = resolve(cfg.ruleset, ctx);
        Double fullHitScaleVal = resolve(cfg.fullHitScale, ctx);

        return new ResolvedAttackConfig(
                enabledVal != null ? enabledVal : true,
                rulesetVal != null ? rulesetVal : Attack.ruleset(),
                cfg.criticalRule != null ? cfg.criticalRule : AttackEvent.CriticalRule.DEFAULT,
                fullHitScaleVal != null ? fullHitScaleVal : AttackConfig.VANILLA_FULL_HIT_SCALE
        );
    }

    private static <T> T resolve(@Nullable FieldValue<AttackContext, T> fv, AttackContext ctx) {
        return fv != null ? fv.resolve(ctx) : null;
    }

    public record ResolvedAttackConfig(
            boolean enabled,
            @Nullable AttackEvent.AttackRule.Ruleset ruleset,
            @Nullable AttackEvent.CriticalRule criticalRule,
            double fullHitScale
    ) {
        public static ResolvedAttackConfig defaults() {
            return new ResolvedAttackConfig(
                    true,
                    Attack.ruleset(),
                    AttackEvent.CriticalRule.DEFAULT,
                    AttackConfig.VANILLA_FULL_HIT_SCALE
            );
        }
    }
}
