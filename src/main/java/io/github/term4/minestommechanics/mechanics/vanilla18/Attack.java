package io.github.term4.minestommechanics.mechanics.vanilla18;

import io.github.term4.minestommechanics.Services;
import io.github.term4.minestommechanics.api.event.attack.AttackEvent;
import io.github.term4.minestommechanics.effect.EffectContext;
import io.github.term4.minestommechanics.effect.Effects;
import io.github.term4.minestommechanics.item.Enchants;
import io.github.term4.minestommechanics.mechanics.attack.AttackConfig;
import io.github.term4.minestommechanics.mechanics.attribute.catalog.enchant.Knockback;
import io.github.term4.minestommechanics.mechanics.damage.DamageSnapshot;
import io.github.term4.minestommechanics.mechanics.damage.DamageSystem;
import io.github.term4.minestommechanics.mechanics.damage.types.melee.MeleeDamage;
import io.github.term4.minestommechanics.mechanics.knockback.KnockbackSnapshot;
import io.github.term4.minestommechanics.mechanics.knockback.KnockbackSystem;
import io.github.term4.minestommechanics.platform.player.OptimizedPlayer;
import io.github.term4.minestommechanics.tracking.SprintTracker;
import io.github.term4.minestommechanics.tracking.motion.MotionTracker;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.Player;

/**
 * Vanilla 1.8 attack config + the legacy (pre-1.9) combat ruleset. The canonical 1.8 values, consumed both by the
 * {@link Vanilla18} preset profile and by {@code AttackConfig}/{@code AttackConfigResolver} as their fallback ruleset.
 */
public final class Attack {

    private Attack() {}

    public static AttackConfig config() {
        return AttackConfig.builder()
                .enabled(true)
                .ruleset(ruleset())
                .criticalRule(AttackEvent.CriticalRule.vanilla())
                .build();
    }

    /** Legacy (pre-1.9) attack ruleset: applies melee damage, knockback, and resets attacker sprint. */
    public static AttackEvent.AttackRule.Ruleset ruleset() {
        return LegacyAttack::new;
    }

    /** Handles legacy (pre-1.9) combat attacks (damage, knockback, sprint reset). */
    private record LegacyAttack(Services services) implements AttackEvent.AttackRule {
        @Override public void processAttack(AttackEvent event) {
            DamageSystem.DamageOutcome result = DamageSystem.DamageOutcome.FRESH_DAMAGE; // no damage system -> nothing absorbs the hit
            DamageSystem dmg = services.damage();
            if (dmg != null && event.target() != null) {
                DamageSnapshot snap = MeleeDamage.INSTANCE.snapshot(
                        event.attacker(), event.target(), event.critical(), event.item(), services);
                result = dmg.apply(snap);
                // crit + magic-crit sparkle on a landed fresh hit (vanilla onCriticalHit / onEnchantmentCritical, independent)
                if (result == DamageSystem.DamageOutcome.FRESH_DAMAGE) {
                    boolean fake = event.finalSnap().aim() != null;
                    EffectContext fx = EffectContext.of(event.attacker(), event.target());
                    if (event.critical()) Effects.play(services, fake ? Effects.FAKE_CRIT : Effects.CRIT, fx);
                    if (MeleeDamage.enchantCritical(event.attacker(), event.target(), event.item(), services)) {
                        Effects.play(services, fake ? Effects.FAKE_MAGIC_CRIT : Effects.MAGIC_CRIT, fx);
                    }
                }
            }
            // Knockback enchant feeds the extra-knockback level (the weapon actually used, frozen for buffered hits; +1 for a sprint hit added in the calculator)
            KnockbackSystem kb = services.knockback();
            if (result == DamageSystem.DamageOutcome.FRESH_DAMAGE && kb != null) {
                int extra = Enchants.level(event.item(), Knockback.KEY);
                Pos aim = event.finalSnap().aim(); // swing-filled hit: KB follows the intersecting ray, not the attacker's live look
                var kbSnap = new KnockbackSnapshot(event.target(), true, event.attacker(), null,
                        aim != null ? aim.direction() : null, null, extra);
                kb.apply(kbSnap);
            }
            // attacker self-effects on a landed sprint/enchant hit: scale own horizontal velocity by fullHitScale (vanilla 0.6) + clear sprint; a non-sprinting hit does neither
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

        /** Whether the attacker had the sprint knockback bonus (vanilla's {@code i > 0} gate). */
        private boolean sprintingForKb(LivingEntity le) {
            if (!(le instanceof Player p)) return false;
            return services.sprintTracker() != null
                    ? SprintTracker.wasRecentlySprinting(services.sprintTracker(), p, 0)
                    : p.isSprinting();
        }
    }
}
