package io.github.term4.minestommechanics.mechanics.damage;

import io.github.term4.minestommechanics.Services;
import io.github.term4.minestommechanics.mechanics.damage.DamageConfigResolver.DamageContext;
import io.github.term4.minestommechanics.mechanics.damage.DamageConfigResolver.ResolvedDamageConfig;

/**
 * Computes the base damage amount for a snapshot (type-specific modifiers are applied by the producer first); the
 * future attribute system will inject weapon/enchant/armor math here. Mirrors KnockbackCalculator.
 */
public final class DamageCalculator {

    private final Services services;
    private final DamageConfig defaults;

    public DamageCalculator(Services services, DamageConfig defaults) {
        this.services = services;
        this.defaults = defaults;
    }

    /** Resolves the config for a snapshot, merging the snapshot's config (if any) over the defaults. */
    public ResolvedDamageConfig resolveConfig(DamageSnapshot snap) {
        DamageConfig base = snap.config() != null ? snap.config() : defaults;
        DamageConfig merged = base.fromBase(defaults);
        return DamageConfigResolver.resolve(merged, DamageContext.of(snap, services));
    }

    /** Computes the base damage amount (type-specific modifiers are applied by the producer). */
    public DamageResult compute(DamageSnapshot snap) {
        ResolvedDamageConfig cfg = resolveConfig(snap);
        return new DamageResult(cfg.baseAmount());
    }

    /** Result of a damage computation. */
    public record DamageResult(float amount) {}
}
