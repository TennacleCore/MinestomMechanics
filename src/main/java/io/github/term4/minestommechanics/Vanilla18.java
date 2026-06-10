package io.github.term4.minestommechanics;

import io.github.term4.echofix.EchoFixPlayer;
import io.github.term4.minestommechanics.api.event.AttackEvent;
import io.github.term4.minestommechanics.mechanics.attack.AttackConfig;
import io.github.term4.minestommechanics.mechanics.damage.DamageSnapshot;
import io.github.term4.minestommechanics.mechanics.damage.DamageConfig;
import io.github.term4.minestommechanics.mechanics.damage.DamageSystem;
import io.github.term4.minestommechanics.mechanics.damage.types.playerattack.PlayerAttack;
import io.github.term4.minestommechanics.mechanics.knockback.KnockbackConfig;
import io.github.term4.minestommechanics.mechanics.knockback.KnockbackSnapshot;
import io.github.term4.minestommechanics.mechanics.knockback.KnockbackSystem;
import io.github.term4.minestommechanics.tracking.VelocityRule;
import net.minestom.server.entity.LivingEntity;

/** Preconfigured vanilla 1.8 values. Use these as defaults when resolving nullable configs. */
public final class Vanilla18 {

    private Vanilla18() {}

    /** Returns an AttackConfig with vanilla 1.8-like values. Buffers disabled (0). */
    public static AttackConfig atk() {
        return AttackConfig.builder()
                .enabled(true)
                .packetHits(true)
                .swingHits(false)
                .sprintBuffer(0)
                .hitQueueBuffer(0)
                .packetReach(10.0)
                .swingReach(3.0)
                .packetPadding(2.0)
                .swingPadding(0.0)
                .ruleset(legacyAttack())
                .criticalRule(AttackEvent.CriticalRule.vanilla())
                .build();
    }

    /** Returns a DamageConfig with vanilla 1.8 values. */
    public static DamageConfig dmg() {
        return DamageConfig.builder()
                .invulTicks(10)
                .enableOverdamage(true)
                .build();
    }

    /**
     * Returns a KnockbackConfig with all vanilla 1.8 values set (no nulls).
     *
     * <p>TODO(modern): notes for a future 1.20+ variant of this preset - the knockback math itself differs from 1.8
     * (verified against 26.1 {@code LivingEntity.knockback} / {@code travelInAir}). Do NOT configure here yet; these
     * are reference notes only:
     * <ul>
     *   <li><b>Vertical fold is grounded-only.</b> 1.8 always folds {@code motY = min(0.4, motY/2 + 0.4)}, airborne or
     *       not; modern only folds when on ground ({@code onGround() ? min(0.4, motY/2 + power) : motY}) - an airborne
     *       hit leaves {@code motY} untouched. Biggest juggle-feel difference; gate the vertical fold + the cap-hold on
     *       the victim's ground state (mirrors KnockbackCalculator's existing vertical-fold TODO).</li>
     *   <li><b>Vertical add tracks horizontal power.</b> 1.8 adds a fixed {@code 0.4}; modern adds {@code power} (the
     *       knockback-resisted horizontal strength), still capped at {@code 0.4}.</li>
     *   <li><b>Knockback resistance is a scale, not a gate.</b> 1.8 rolls {@code random >= resistance} and skips KB
     *       entirely on success; modern always scales {@code power *= (1 - resistance)}.</li>
     *   <li><b>Near-zero motion clamp {@code 0.005 -> 0.003}</b> ({@code MIN_MOVEMENT_DISTANCE}), plus a modern
     *       near-apex gravity micro-step ({@code -0.003} / {@code gravity/16}) that 1.8 lacks - nudges the apex.</li>
     *   <li>Decay law is unchanged across versions: {@code motY = (motY - 0.08) * 0.98} each air tick.</li>
     * </ul>
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
                .degenerateFallback(KnockbackConfig.DegenerateFallback.RANDOM)
                .frictionH(2.0)
                .frictionV(2.0)
                .frictionModeH(KnockbackConfig.FrictionMode.DIVISOR)
                .frictionModeV(KnockbackConfig.FrictionMode.DIVISOR)
                .rangeStartH(0.0)
                .rangeFactorH(0.0)
                .rangeStartV(0.0)
                .rangeFactorV(0.0)
                .rangeStartExtraH(0.0)
                .rangeFactorExtraH(0.0)
                .rangeStartExtraV(0.0)
                .rangeFactorExtraV(0.0)
                .rangeMaxH(0.0)
                .rangeMaxV(0.0)
                .rangeMaxExtraH(0.0)
                .rangeMaxExtraV(0.0)
                .sweepFactorH(0.0)
                .sweepFactorV(0.0)
                .sweepFactorExtraH(0.0)
                .sweepFactorExtraV(0.0)
                .knockbackFormula(KnockbackConfig.KnockbackFormula.CLASSIC)
                .velocity(VelocityRule.simulated())
                .build();
    }

    /** Legacy (pre-1.9) attack ruleset: applies melee damage, knockback, and resets attacker sprint. */
    public static AttackEvent.AttackRule.Ruleset legacyAttack() {
        return LegacyAttack::new;
    }

    /** Handles legacy (pre-1.9) combat attacks (damage, knockback, sprint reset). */
    private record LegacyAttack(Services services) implements AttackEvent.AttackRule {
        @Override public void processAttack(AttackEvent event) {
            // 1. Damage (crit determined in the attack layer; melee damage type builds the snapshot)
            DamageSystem dmg = services.damage();
            if (dmg != null && event.target() != null) {
                DamageSnapshot snap = PlayerAttack.INSTANCE.snapshot(event.attacker(), event.target(), event.critical(), services);
                dmg.apply(snap);
            }
            // 2. Knockback
            KnockbackSystem kb = services.knockback();
            if (kb != null) {
                var kbSnap = new KnockbackSnapshot(event.target(), event.cause(), event.attacker(), null, null, kb.config());
                kb.apply(kbSnap);
                if (kbSnap.target().isOnGround()) {
                    System.out.println("WAS ON GROUND");
                } else {
                    System.out.println("WAS IN AIRs");
                }
            }
            // 3. Reset attacker sprint
            if (event.attacker() instanceof LivingEntity le) {
                if (le instanceof EchoFixPlayer efp) {
                    efp.suppressSelf(() -> efp.setSprinting(false));
                } else {
                    le.setSprinting(false);
                }
            }
        }
    }
}
