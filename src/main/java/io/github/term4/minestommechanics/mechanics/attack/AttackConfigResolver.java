package io.github.term4.minestommechanics.mechanics.attack;

import io.github.term4.minestommechanics.Services;
import io.github.term4.minestommechanics.mechanics.Vanilla18;
import io.github.term4.minestommechanics.api.event.AttackEvent;
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

        // no attack-level invul/buffering: attacks always process; the damage/knockback systems gate themselves
        Boolean enabledVal = resolve(cfg.enabled, ctx);
        AttackEvent.AttackRule.Ruleset rulesetVal = resolve(cfg.ruleset, ctx);
        Double fullHitScaleVal = resolve(cfg.fullHitScale, ctx);

        return new ResolvedAttackConfig(
                enabledVal != null ? enabledVal : true,
                rulesetVal != null ? rulesetVal : Vanilla18.legacyAttack(),
                cfg.criticalRule != null ? cfg.criticalRule : AttackEvent.CriticalRule.DEFAULT,
                fullHitScaleVal != null ? fullHitScaleVal : 0.6
        );
    }

    private static <T> T resolve(@Nullable FieldValue<AttackContext, T> fv, AttackContext ctx) {
        return fv != null ? fv.resolve(ctx) : null;
    }

    /** Resolved config with plain values. Used by AttackEvent and AttackSystem. */
    public record ResolvedAttackConfig(
            boolean enabled,
            @Nullable AttackEvent.AttackRule.Ruleset ruleset,
            @Nullable AttackEvent.CriticalRule criticalRule,
            double fullHitScale
    ) {
        public static ResolvedAttackConfig defaults() {
            return new ResolvedAttackConfig(
                    true,
                    Vanilla18.legacyAttack(),
                    AttackEvent.CriticalRule.DEFAULT,
                    0.6
            );
        }
    }
}
