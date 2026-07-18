package io.github.term4.minestommechanics.presets.vanilla;

import io.github.term4.minestommechanics.mechanics.attribute.AttributeConfig;
import io.github.term4.minestommechanics.mechanics.attribute.catalog.VanillaAttributes;
import io.github.term4.minestommechanics.mechanics.attribute.catalog.effect.Absorption;
import io.github.term4.minestommechanics.mechanics.attribute.catalog.effect.Haste;
import io.github.term4.minestommechanics.mechanics.attribute.catalog.effect.JumpBoost;
import io.github.term4.minestommechanics.mechanics.attribute.catalog.effect.MiningFatigue;
import io.github.term4.minestommechanics.mechanics.attribute.catalog.effect.Strength;
import io.github.term4.minestommechanics.mechanics.attribute.catalog.effect.Weakness;
import io.github.term4.minestommechanics.mechanics.attribute.catalog.enchant.Sharpness;
import io.github.term4.minestommechanics.mechanics.attribute.defense.ArmorConfig;
import io.github.term4.minestommechanics.mechanics.attribute.defense.ProtectionConfig;

/**
 * Modern (26.1) attribute config: MODERN flavor + the modern source variants. Unlike {@code vanilla18.Attributes}, this
 * includes Haste / Mining Fatigue (attack-speed) and Jump Boost (safe-fall-distance), which are attribute-based in 26 but
 * dig-speed / jump-velocity mechanics in 1.8 (so absent from the legacy catalog until those systems exist).
 */
public final class Attributes {

    private Attributes() {}

    public static AttributeConfig config() {
        return AttributeConfig.builder()
                .sources(Strength.MODERN, Weakness.MODERN, Sharpness.MODERN, Absorption.MODERN) // 26 version variants
                .sources(VanillaAttributes.enchants())
                .sources(VanillaAttributes.effects())
                .sources(Haste.MODERN, MiningFatigue.MODERN, JumpBoost.MODERN) // modern-only: Via strips these for 1.8
                .armor(ArmorConfig.builder().formula(ArmorConfig.Formula.MODERN_TOUGHNESS).build())
                .protection(ProtectionConfig.builder().formula(ProtectionConfig.Formula.MODERN_LINEAR).build())
                .build();
    }
}
