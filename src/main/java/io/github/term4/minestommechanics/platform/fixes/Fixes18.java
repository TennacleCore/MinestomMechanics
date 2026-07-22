package io.github.term4.minestommechanics.platform.fixes;

import io.github.term4.minestommechanics.platform.fixes.client.InventorySyncFixConfig;
import io.github.term4.minestommechanics.platform.fixes.client.LegacyConsumeFixConfig;
import io.github.term4.minestommechanics.platform.fixes.client.LegacyEquipmentFixConfig;
import io.github.term4.minestommechanics.platform.fixes.client.LegacyTabCompleteFixConfig;
import io.github.term4.minestommechanics.platform.fixes.client.SelfPlacementFixConfig;
import io.github.term4.minestommechanics.platform.fixes.visuals.VisualsConfig;
import io.github.term4.minestommechanics.platform.fixes.visuals.legacy_1_8.LegacyArrowVisibilityConfig;

/** The full 1.8/Via legacy-client {@link FixesConfig}; install with {@code FixesSystem.install(mm, Fixes18.config())}. */
public final class Fixes18 {

    private Fixes18() {}

    public static FixesConfig config() {
        return FixesConfig.builder()
                .visuals(VisualsConfig.builder()
                        .legacyArrowVisibility(LegacyArrowVisibilityConfig.builder().enabled(true).deflectParticles(true).build())
                        .build())
                .selfPlacement(SelfPlacementFixConfig.builder().enabled(true).build())
                .legacyEquipmentFix(LegacyEquipmentFixConfig.builder().enabled(true).build())
                .legacyTabCompleteFix(LegacyTabCompleteFixConfig.builder().enabled(true).build())
                .legacyConsume(LegacyConsumeFixConfig.builder().enabled(true).build())
                .inventorySync(InventorySyncFixConfig.builder().enabled(true).build()) // EXPERIMENTAL
                .build();
    }
}
