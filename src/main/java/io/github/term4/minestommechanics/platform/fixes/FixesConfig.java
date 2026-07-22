package io.github.term4.minestommechanics.platform.fixes;

import io.github.term4.minestommechanics.platform.fixes.client.InventorySyncFixConfig;
import io.github.term4.minestommechanics.platform.fixes.client.LegacyConsumeFixConfig;
import io.github.term4.minestommechanics.platform.fixes.client.LegacyEquipmentFixConfig;
import io.github.term4.minestommechanics.platform.fixes.client.LegacyTabCompleteFixConfig;
import io.github.term4.minestommechanics.platform.fixes.client.SelfPlacementFixConfig;
import io.github.term4.minestommechanics.platform.fixes.visuals.VisualsConfig;
import org.jetbrains.annotations.Nullable;

/**
 * Top-level config for the lib's client/protocol behavior <b>fixes</b> - both cross-version compatibility (the 1.8
 * arrow-visibility fix) and single-version client smoothing (the self-meta echo fix). Assigned per scope via the
 * {@link io.github.term4.minestommechanics.MechanicsProfile} {@code fixes} member.
 */
public final class FixesConfig {

    private final @Nullable VisualsConfig visuals;
    private final @Nullable SelfPlacementFixConfig selfPlacement;
    private final @Nullable LegacyEquipmentFixConfig legacyEquipmentFix;
    private final @Nullable LegacyTabCompleteFixConfig legacyTabCompleteFix;
    private final @Nullable LegacyConsumeFixConfig legacyConsume;
    private final @Nullable InventorySyncFixConfig inventorySync;

    private FixesConfig(Builder b) {
        this.visuals = b.visuals;
        this.selfPlacement = b.selfPlacement;
        this.legacyEquipmentFix = b.legacyEquipmentFix;
        this.legacyTabCompleteFix = b.legacyTabCompleteFix;
        this.legacyConsume = b.legacyConsume;
        this.inventorySync = b.inventorySync;
    }

    public @Nullable VisualsConfig visuals() { return visuals; }
    public @Nullable SelfPlacementFixConfig selfPlacement() { return selfPlacement; }
    public @Nullable LegacyEquipmentFixConfig legacyEquipmentFix() { return legacyEquipmentFix; }
    public @Nullable LegacyTabCompleteFixConfig legacyTabCompleteFix() { return legacyTabCompleteFix; }
    public @Nullable LegacyConsumeFixConfig legacyConsume() { return legacyConsume; }
    public @Nullable InventorySyncFixConfig inventorySync() { return inventorySync; }

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
        LegacyConsumeFixConfig lc = legacyConsume == null ? base.legacyConsume
                : base.legacyConsume == null ? legacyConsume : legacyConsume.fromBase(base.legacyConsume);
        InventorySyncFixConfig is = inventorySync == null ? base.inventorySync
                : base.inventorySync == null ? inventorySync : inventorySync.fromBase(base.inventorySync);
        return new Builder().visuals(v).selfPlacement(sp).legacyEquipmentFix(le).legacyTabCompleteFix(ltc).legacyConsume(lc).inventorySync(is).build();
    }

    public Builder toBuilder() { return new Builder(this); }
    public static Builder builder() { return new Builder(); }
    public static Builder builder(@Nullable FixesConfig base) { return base != null ? new Builder(base) : new Builder(); }

    public static final class Builder {
        private @Nullable VisualsConfig visuals;
        private @Nullable SelfPlacementFixConfig selfPlacement;
        private @Nullable LegacyEquipmentFixConfig legacyEquipmentFix;
        private @Nullable LegacyTabCompleteFixConfig legacyTabCompleteFix;
        private @Nullable LegacyConsumeFixConfig legacyConsume;
        private @Nullable InventorySyncFixConfig inventorySync;

        Builder() {}
        Builder(FixesConfig c) { visuals = c.visuals; selfPlacement = c.selfPlacement; legacyEquipmentFix = c.legacyEquipmentFix; legacyTabCompleteFix = c.legacyTabCompleteFix; legacyConsume = c.legacyConsume; inventorySync = c.inventorySync; }

        public Builder visuals(@Nullable VisualsConfig v) { this.visuals = v; return this; }
        public Builder selfPlacement(@Nullable SelfPlacementFixConfig v) { this.selfPlacement = v; return this; }
        public Builder legacyEquipmentFix(@Nullable LegacyEquipmentFixConfig v) { this.legacyEquipmentFix = v; return this; }
        public Builder legacyTabCompleteFix(@Nullable LegacyTabCompleteFixConfig v) { this.legacyTabCompleteFix = v; return this; }
        public Builder legacyConsume(@Nullable LegacyConsumeFixConfig v) { this.legacyConsume = v; return this; }
        public Builder inventorySync(@Nullable InventorySyncFixConfig v) { this.inventorySync = v; return this; }

        public FixesConfig build() { return new FixesConfig(this); }
    }
}
