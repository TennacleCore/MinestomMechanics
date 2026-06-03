package io.github.term4.minestommechanics.mechanics.attack;

import io.github.term4.minestommechanics.Services;
import io.github.term4.minestommechanics.Vanilla18;
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

        // Attack invul defaults to 0: the damage and knockback systems own their own invul windows.
        Integer atkInvulVal = resolve(cfg.atkInvulnTicks, ctx);
        if (atkInvulVal == null) atkInvulVal = 0;

        Integer hitQueueVal = resolve(cfg.hitQueueBuffer, ctx);
        if (hitQueueVal == null) hitQueueVal = 0;

        Boolean enabledVal = resolve(cfg.enabled, ctx);
        Boolean packetHitsVal = resolve(cfg.packetHits, ctx);
        Boolean swingHitsVal = resolve(cfg.swingHits, ctx);
        Double packetReachVal = resolve(cfg.packetReach, ctx);
        Double swingReachVal = resolve(cfg.swingReach, ctx);
        Double packetPaddingVal = resolve(cfg.packetPadding, ctx);
        Double swingPaddingVal = resolve(cfg.swingPadding, ctx);
        AttackEvent.AttackRule.Ruleset rulesetVal = resolve(cfg.ruleset, ctx);

        return new ResolvedAttackConfig(
                enabledVal != null ? enabledVal : true,
                resolve(cfg.idleTimeout, ctx),
                atkInvulVal,
                resolve(cfg.sprintBuffer, ctx),
                hitQueueVal,
                packetHitsVal != null ? packetHitsVal : true,
                swingHitsVal != null ? swingHitsVal : false,
                packetReachVal != null ? packetReachVal : 10.0,
                swingReachVal != null ? swingReachVal : 3.0,
                packetPaddingVal != null ? packetPaddingVal : 2.0,
                swingPaddingVal != null ? swingPaddingVal : 0.0,
                rulesetVal != null ? rulesetVal : Vanilla18.legacyAttack(),
                cfg.criticalRule != null ? cfg.criticalRule : AttackEvent.CriticalRule.DEFAULT,
                cfg.hitQueueInvulSource != null ? cfg.hitQueueInvulSource : AttackConfig.HitQueueInvulSource.AUTO
        );
    }

    private static <T> T resolve(@Nullable FieldValue<AttackContext, T> fv, AttackContext ctx) {
        return fv != null ? fv.resolve(ctx) : null;
    }

    /** Resolved config with plain values. Used by AttackEvent and AttackSystem. */
    public record ResolvedAttackConfig(
            boolean enabled,
            @Nullable Integer idleTimeout,
            int atkInvulnTicks,
            @Nullable Integer sprintBuffer,
            int hitQueueBuffer,
            boolean packetHits,
            boolean swingHits,
            double packetReach,
            double swingReach,
            double packetPadding,
            double swingPadding,
            @Nullable AttackEvent.AttackRule.Ruleset ruleset,
            @Nullable AttackEvent.CriticalRule criticalRule,
            AttackConfig.HitQueueInvulSource hitQueueInvulSource
    ) {
        public static ResolvedAttackConfig defaults() {
            return new ResolvedAttackConfig(
                    true,
                    null,
                    0,
                    null,
                    0,
                    true,
                    false,
                    10.0,
                    3.0,
                    2.0,
                    0.0,
                    Vanilla18.legacyAttack(),
                    AttackEvent.CriticalRule.DEFAULT,
                    AttackConfig.HitQueueInvulSource.AUTO
            );
        }
    }
}
