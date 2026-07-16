package io.github.term4.minestommechanics.platform.fixes;

import io.github.term4.minestommechanics.platform.fixes.client.InventorySyncFixConfig;
import io.github.term4.minestommechanics.platform.fixes.client.LegacyConsumeFixConfig;
import io.github.term4.minestommechanics.platform.fixes.client.LegacyEquipmentFixConfig;
import io.github.term4.minestommechanics.platform.fixes.client.LegacyTabCompleteFixConfig;
import io.github.term4.minestommechanics.platform.fixes.client.SelfPlacementFixConfig;
import io.github.term4.minestommechanics.platform.fixes.visuals.VisualsConfig;
import io.github.term4.minestommechanics.platform.fixes.visuals.legacy_1_8.LegacyArrowVisibilityConfig;

/**
 * Legacy-client fix bundle: the {@link FixesConfig} that makes 1.8/Via clients render and behave correctly - arrow
 * visibility, self-placement, and the legacy equipment / tab-complete fixes.
 * General; install with {@code FixesSystem.install(mm, Fixes18.config())}.
 */
public final class Fixes18 {

    private Fixes18() {}

    /** The full legacy-client fix set. */
    public static FixesConfig config() {
        return FixesConfig.builder()
                .visuals(VisualsConfig.builder()
                        .legacyArrowVisibility(LegacyArrowVisibilityConfig.builder().enabled(true).deflectParticles(true).build())
                        .build())
                .selfPlacement(SelfPlacementFixConfig.builder().enabled(true).build()) // For 1.8 clients so they can place ladders while climbing up
                .legacyEquipmentFix(LegacyEquipmentFixConfig.builder().enabled(true).build()) // strip empty equip slots for legacy clients (fixes BODY=AIR -> chestplate-invisible)
                .legacyTabCompleteFix(LegacyTabCompleteFixConfig.builder().enabled(true).build()) // answer command-name tab completion that Minestom ignores (fixes /gm -> gmc/gms on 1.8/Via)
                .legacyConsume(LegacyConsumeFixConfig.builder().enabled(true).build()) // 1.8/Via eating under lag: no double-eat, no count race (modern clients gate themselves)
                .inventorySync(InventorySyncFixConfig.builder().enabled(true).build()) // EXPERIMENTAL: suppress inventory slot echoes the client already predicts (fixes the high-lag move-and-revert flicker)
                .build();
    }
}
