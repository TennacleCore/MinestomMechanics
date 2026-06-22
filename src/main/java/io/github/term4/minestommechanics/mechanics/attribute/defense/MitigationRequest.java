package io.github.term4.minestommechanics.mechanics.attribute.defense;

import java.util.Random;
import java.util.Set;

/**
 * The per-hit inputs the damage side hands the attribute system's {@link io.github.term4.minestommechanics.mechanics.attribute.AttributeSystem#mitigate
 * mitigation pipeline}: the damage's protection {@link ProtectionCategory categories} (for EPF gating), the {@link Bypass}
 * spec (which stages / specific attributes-effects-enchants the hit ignores), and the {@code random} that drives the 1.8
 * EPF roll.
 */
public record MitigationRequest(Set<ProtectionCategory> categories, Bypass bypass, Random random) {

    public static MitigationRequest of(Set<ProtectionCategory> categories, Bypass bypass, Random random) {
        return new MitigationRequest(categories, bypass != null ? bypass : Bypass.NONE, random);
    }
}
