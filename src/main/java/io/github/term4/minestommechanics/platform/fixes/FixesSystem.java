package io.github.term4.minestommechanics.platform.fixes;

import io.github.term4.minestommechanics.MechanicsKeys;
import io.github.term4.minestommechanics.MechanicsModule;
import io.github.term4.minestommechanics.MinestomMechanics;
import io.github.term4.minestommechanics.platform.fixes.client.InventorySync;
import io.github.term4.minestommechanics.platform.fixes.client.LegacyEquipmentFix;
import io.github.term4.minestommechanics.platform.fixes.client.LegacyTabCompleteFix;
import io.github.term4.minestommechanics.platform.fixes.client.SelfPlacementFix;
import io.github.term4.minestommechanics.platform.fixes.visuals.VisualsConfig;
import io.github.term4.minestommechanics.platform.fixes.visuals.legacy_1_8.LegacyArrowVisibility;
import io.github.term4.minestommechanics.platform.fixes.visuals.legacy_1_8.LegacyArrowVisibilityConfig;
import net.minestom.server.entity.Entity;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Installs the client/protocol behavior fixes from a {@link FixesConfig}; per-scope config via {@code FIXES}.
 *
 * <p>The self-meta smoothing fix is delivered by the custom player override, so it is armed by
 * {@code MetaFix.installListeners()} from {@code MinestomMechanics.init}, not here.
 */
public final class FixesSystem implements MechanicsModule {

    private final MinestomMechanics mm;
    private final FixesConfig config;
    private final EventNode<@NotNull Event> node;
    private final LegacyArrowVisibility legacyArrowVisibility;

    public FixesSystem(MinestomMechanics mm, FixesConfig config) {
        this.mm = mm;
        this.config = config;
        this.node = EventNode.all("mm:fixes");
        this.legacyArrowVisibility = new LegacyArrowVisibility(this);
    }

    public EventNode<@NotNull Event> node() { return node; }
    public FixesConfig config() { return config; }
    public LegacyArrowVisibility legacyArrowVisibility() { return legacyArrowVisibility; }

    /** Effective config for {@code subject}: the scoped profile, else the install config. */
    public FixesConfig configFor(@Nullable Entity subject) {
        return mm.profiles().resolveOr(subject, MechanicsKeys.FIXES, config);
    }

    public @Nullable LegacyArrowVisibilityConfig legacyArrowVisibilityConfig(@Nullable Entity subject) {
        VisualsConfig v = configFor(subject).visuals();
        return v != null ? v.legacyArrowVisibility() : null;
    }

    /** Whether the legacy arrow-visibility team fix is enabled for {@code subject} (default {@code false}). */
    public boolean legacyArrowVisibilityEnabled(@Nullable Entity subject) {
        LegacyArrowVisibilityConfig c = legacyArrowVisibilityConfig(subject);
        return c != null && Boolean.TRUE.equals(c.enabled());
    }

    /** Whether the cosmetic deflect crit-trail is enabled for {@code subject} (default {@code false}). */
    public boolean legacyArrowDeflectParticles(@Nullable Entity subject) {
        LegacyArrowVisibilityConfig c = legacyArrowVisibilityConfig(subject);
        return c != null && Boolean.TRUE.equals(c.deflectParticles());
    }

    /** Installs from the GLOBAL profile's {@link FixesConfig} - set the profile before installing. */
    public static FixesSystem install(MinestomMechanics mm) {
        FixesConfig global = mm.profiles().resolve(null, MechanicsKeys.FIXES);
        return install(mm, global != null ? global : FixesConfig.builder().build());
    }

    public static FixesSystem install(MinestomMechanics mm, FixesConfig cfg) {
        FixesSystem system = new FixesSystem(mm, cfg);
        mm.register(system);
        system.legacyArrowVisibility.install(system.node);
        // Below ride server-wide listeners / send overrides, so they gate on the install config and cannot vary per scope.
        // Self-placement wraps the STOCK placement listener; an app that replaces that listener re-installs LAST with
        // its own as the delegate.
        if (enabled(cfg.selfPlacement())) SelfPlacementFix.install();
        if (enabled(cfg.legacyEquipmentFix())) LegacyEquipmentFix.install();
        if (enabled(cfg.legacyTabCompleteFix())) LegacyTabCompleteFix.install();
        if (enabled(cfg.inventorySync())) InventorySync.install(system.node);
        mm.install(system.node);
        return system;
    }

    private static boolean enabled(@Nullable FixToggle cfg) {
        return cfg != null && Boolean.TRUE.equals(cfg.enabled());
    }
}
