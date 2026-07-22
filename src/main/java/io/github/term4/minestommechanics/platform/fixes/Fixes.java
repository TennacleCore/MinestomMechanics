package io.github.term4.minestommechanics.platform.fixes;

import io.github.term4.minestommechanics.platform.fixes.visuals.VisualsConfig;
import io.github.term4.minestommechanics.platform.fixes.visuals.legacy_1_8.LegacyArrowVisibilityConfig;

/**
 * Ready-made {@link FixesConfig} presets; pass to {@code FixesSystem.install} or a {@code MechanicsProfile.fixes}
 * scope, or override with the builders for a subset.
 */
public final class Fixes {

    private Fixes() {}

    /** The recommended 1.8-client fixes; the cosmetic deflect crit-trail stays off (a flourish, not a fix). */
    public static FixesConfig legacy18() {
        return FixesConfig.builder()
                .visuals(VisualsConfig.builder()
                        .legacyArrowVisibility(LegacyArrowVisibilityConfig.builder().enabled(true).build())
                        .build())
                .build();
    }
}
