package io.github.term4.minestommechanics.platform.fixes;

import io.github.term4.minestommechanics.platform.fixes.visuals.VisualsConfig;
import io.github.term4.minestommechanics.platform.fixes.visuals.legacy_1_8.LegacyArrowVisibilityConfig;

/**
 * Out-of-the-box {@link FixesConfig} presets, so a server can turn on the recommended client/protocol fixes
 * without hand-building the config tree (constant-only, like {@code Vanilla18}). Pass one to
 * {@link FixesSystem#install(io.github.term4.minestommechanics.MinestomMechanics, FixesConfig)} or set it
 * on a {@code MechanicsProfile.fixes} scope; compose / override with the builders for a subset.
 */
public final class Fixes {

    private Fixes() {}

    /**
     * The recommended 1.8-client fixes on: the legacy arrow-visibility fix (co-teams players so a 1.8 client
     * stops hiding deflected / passed-through arrows - purely visual, server damage ignores teams). The cosmetic
     * deflect crit-trail is left off (an opt-in Hypixel-style flourish, not a fix). More 1.8 visual / interaction
     * fixes will default-on here as they are added.
     */
    public static FixesConfig legacy18() {
        return FixesConfig.builder()
                .visuals(VisualsConfig.builder()
                        .legacyArrowVisibility(LegacyArrowVisibilityConfig.builder().enabled(true).build())
                        .build())
                .build();
    }
}
