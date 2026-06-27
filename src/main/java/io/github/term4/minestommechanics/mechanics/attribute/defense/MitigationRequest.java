package io.github.term4.minestommechanics.mechanics.attribute.defense;

import java.util.Random;
import java.util.Set;

/**
 * The per-hit inputs the damage side hands {@link io.github.term4.minestommechanics.mechanics.attribute.AttributeSystem#mitigate}:
 * the protection {@link ProtectionCategory categories} (EPF gating), the {@link Bypass} spec (stages/keys the hit ignores),
 * and the {@code random} that drives the 1.8 EPF roll.
 */
public record MitigationRequest(Set<ProtectionCategory> categories, Bypass bypass, Random random) {

    public static MitigationRequest of(Set<ProtectionCategory> categories, Bypass bypass, Random random) {
        return new MitigationRequest(categories, bypass != null ? bypass : Bypass.NONE, random);
    }
}
