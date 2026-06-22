package io.github.term4.minestommechanics.mechanics.attribute.catalog;

import io.github.term4.minestommechanics.mechanics.attribute.source.Source;
import io.github.term4.minestommechanics.mechanics.attribute.catalog.enchant.AquaAffinity;
import io.github.term4.minestommechanics.mechanics.attribute.catalog.enchant.Bane;
import io.github.term4.minestommechanics.mechanics.attribute.catalog.enchant.DepthStrider;
import io.github.term4.minestommechanics.mechanics.attribute.catalog.enchant.Efficiency;
import io.github.term4.minestommechanics.mechanics.attribute.catalog.enchant.FireAspect;
import io.github.term4.minestommechanics.mechanics.attribute.catalog.enchant.Smite;
import io.github.term4.minestommechanics.mechanics.attribute.catalog.effect.Blindness;
import io.github.term4.minestommechanics.mechanics.attribute.catalog.effect.InstantDamage;
import io.github.term4.minestommechanics.mechanics.attribute.catalog.effect.InstantHealth;
import io.github.term4.minestommechanics.mechanics.attribute.catalog.effect.Invisibility;
import io.github.term4.minestommechanics.mechanics.attribute.catalog.effect.NightVision;
import io.github.term4.minestommechanics.mechanics.attribute.catalog.effect.Regeneration;
import io.github.term4.minestommechanics.mechanics.attribute.catalog.effect.Slowness;
import io.github.term4.minestommechanics.mechanics.attribute.catalog.effect.Speed;

/**
 * Vanilla attribute-{@link Source} bundles shared across presets - the version-agnostic enchants/effects both
 * {@code Vanilla18} and {@code Vanilla} register, grouped so each preset's {@code attributes()} composes from named groups
 * (the way the damage presets compose {@code typeConfigs}) instead of a flat wall. Mirrors {@code damage.types.VanillaTypes}
 * / {@code item.VanillaItems}. The version-specific variants ({@code Strength.LEGACY} vs {@code .MODERN}, Absorption, and
 * the modern-only Haste / Mining Fatigue / Jump Boost) stay inline in the preset, since the version lives in what's registered.
 */
public final class VanillaAttributes {
    private VanillaAttributes() {}

    /** Version-agnostic enchants (same instance in every preset): conditional weapon (Smite/Bane) + utility (Efficiency, Aqua Affinity, Depth Strider, Fire Aspect). */
    public static Source[] enchants() {
        return new Source[]{Smite.INSTANCE, Bane.INSTANCE, Efficiency.INSTANCE, AquaAffinity.INSTANCE,
                DepthStrider.INSTANCE, FireAspect.INSTANCE};
    }

    /** Version-agnostic potion effects: server-side (Speed/Slowness movement, Invisibility, Regeneration, Instant Health) + client-rendered (Blindness, Night Vision). */
    public static Source[] effects() {
        return new Source[]{Speed.INSTANCE, Slowness.INSTANCE, Invisibility.INSTANCE, Regeneration.INSTANCE,
                InstantHealth.INSTANCE, InstantDamage.INSTANCE, Blindness.INSTANCE, NightVision.INSTANCE};
    }
}
