package io.github.term4.minestommechanics.platform.fixes;

import io.github.term4.minestommechanics.platform.fixes.client.LegacyEquipmentFixConfig;
import io.github.term4.minestommechanics.platform.fixes.client.LegacyTabCompleteFixConfig;
import io.github.term4.minestommechanics.platform.fixes.client.SelfPlacementFixConfig;
import io.github.term4.minestommechanics.platform.fixes.visuals.VisualsConfig;
import org.jetbrains.annotations.Nullable;

/**
 * Top-level config for the lib's client/protocol behavior <b>fixes</b> - covering both cross-version compatibility
 * fixes (papering over how an older client behaves, e.g. the 1.8 arrow-visibility fix) and single-version client
 * smoothing (a modern client's own prediction echo, e.g. the self-meta fix). Assigned per scope via the
 * {@link io.github.term4.minestommechanics.MechanicsProfile} {@code fixes} member.
 */
public final class FixesConfig {

    private final @Nullable VisualsConfig visuals;
    private final @Nullable SelfPlacementFixConfig selfPlacement;
    private final @Nullable LegacyEquipmentFixConfig legacyEquipmentFix;
    private final @Nullable LegacyTabCompleteFixConfig legacyTabCompleteFix;

    private FixesConfig(Builder b) {
        this.visuals = b.visuals;
        this.selfPlacement = b.selfPlacement;
        this.legacyEquipmentFix = b.legacyEquipmentFix;
        this.legacyTabCompleteFix = b.legacyTabCompleteFix;
    }

    /** The visual fixes, or {@code null} if unset. */
    public @Nullable VisualsConfig visuals() { return visuals; }
    /** The 1.8 self-placement compat fix (place a block into your own body), or {@code null} if unset. */
    public @Nullable SelfPlacementFixConfig selfPlacement() { return selfPlacement; }
    /** The legacy equipment-slot fix (strip empty slots from equipment packets to legacy clients), or {@code null} if unset. */
    public @Nullable LegacyEquipmentFixConfig legacyEquipmentFix() { return legacyEquipmentFix; }
    /** The tab-completion fix (answer command-name completion that Minestom ignores) for legacy clients, or {@code null} if unset. */
    public @Nullable LegacyTabCompleteFixConfig legacyTabCompleteFix() { return legacyTabCompleteFix; }

    /** Merges this config over {@code base} (each member: this if set, else base; both set -&gt; member-merged). */
    public FixesConfig fromBase(FixesConfig base) {
        VisualsConfig v = visuals == null ? base.visuals
                : base.visuals == null ? visuals : visuals.fromBase(base.visuals);
        SelfPlacementFixConfig sp = selfPlacement == null ? base.selfPlacement
                : base.selfPlacement == null ? selfPlacement : selfPlacement.fromBase(base.selfPlacement);
        LegacyEquipmentFixConfig le = legacyEquipmentFix == null ? base.legacyEquipmentFix
                : base.legacyEquipmentFix == null ? legacyEquipmentFix : legacyEquipmentFix.fromBase(base.legacyEquipmentFix);
        LegacyTabCompleteFixConfig ltc = legacyTabCompleteFix == null ? base.legacyTabCompleteFix
                : base.legacyTabCompleteFix == null ? legacyTabCompleteFix : legacyTabCompleteFix.fromBase(base.legacyTabCompleteFix);
        return new Builder().visuals(v).selfPlacement(sp).legacyEquipmentFix(le).legacyTabCompleteFix(ltc).build();
    }

    public Builder toBuilder() { return new Builder(this); }
    public static Builder builder() { return new Builder(); }
    public static Builder builder(@Nullable FixesConfig base) { return base != null ? new Builder(base) : new Builder(); }

    public static final class Builder {
        private @Nullable VisualsConfig visuals;
        private @Nullable SelfPlacementFixConfig selfPlacement;
        private @Nullable LegacyEquipmentFixConfig legacyEquipmentFix;
        private @Nullable LegacyTabCompleteFixConfig legacyTabCompleteFix;

        Builder() {}
        Builder(FixesConfig c) { visuals = c.visuals; selfPlacement = c.selfPlacement; legacyEquipmentFix = c.legacyEquipmentFix; legacyTabCompleteFix = c.legacyTabCompleteFix; }

        public Builder visuals(@Nullable VisualsConfig v) { this.visuals = v; return this; }
        public Builder selfPlacement(@Nullable SelfPlacementFixConfig v) { this.selfPlacement = v; return this; }
        public Builder legacyEquipmentFix(@Nullable LegacyEquipmentFixConfig v) { this.legacyEquipmentFix = v; return this; }
        public Builder legacyTabCompleteFix(@Nullable LegacyTabCompleteFixConfig v) { this.legacyTabCompleteFix = v; return this; }

        public FixesConfig build() { return new FixesConfig(this); }
    }
}
