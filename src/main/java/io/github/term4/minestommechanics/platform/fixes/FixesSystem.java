package io.github.term4.minestommechanics.platform.fixes;

import io.github.term4.minestommechanics.MinestomMechanics;
import io.github.term4.minestommechanics.platform.fixes.client.SelfPlacementFix;
import io.github.term4.minestommechanics.platform.fixes.visuals.VisualsConfig;
import io.github.term4.minestommechanics.platform.fixes.visuals.legacy_1_8.LegacyArrowVisibility;
import io.github.term4.minestommechanics.platform.fixes.visuals.legacy_1_8.LegacyArrowVisibilityConfig;
import io.github.term4.minestommechanics.platform.fixes.world.BlockPlacementFix;
import net.minestom.server.entity.Entity;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Installs the client/protocol behavior fixes from a {@link FixesConfig}. Mirrors the other systems
 * (DamageSystem / ProjectileSystem): registers itself on {@code mm}, owns an {@code EventNode}, and wires each fix's
 * manager. Per-scope config resolves through {@code MechanicsProfiles.fixesFor} (player -&gt; instance -&gt; global),
 * with the install config as the final fallback. Today the only managed fix is the 1.8 legacy arrow-visibility
 * ({@link LegacyArrowVisibility}); future fixes register their own managers here.
 *
 * <p>(The self-meta smoothing fix lives in {@code fixes.client} but its delivery is the custom player override, so it
 * is armed by {@code MetaFix.installListeners()} from {@code MinestomMechanics.init} rather than this system.)
 */
public final class FixesSystem {

    private final MinestomMechanics mm;
    private final FixesConfig config; // install config (the resolution fallback)
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
    /** The 1.8 legacy arrow-visibility manager (runtime on/off + per-player team sync). */
    public LegacyArrowVisibility legacyArrowVisibility() { return legacyArrowVisibility; }

    /** Effective fixes config for {@code subject}: the scoped profile (player -&gt; instance -&gt; global), else the install config. */
    public FixesConfig configFor(@Nullable Entity subject) {
        FixesConfig scoped = mm.profiles().fixesFor(subject);
        return scoped != null ? scoped : config;
    }

    /** The resolved 1.8 legacy arrow-visibility config for {@code subject} ({@code visuals.legacyArrowVisibility}), or {@code null}. */
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

    /** Installs with no install-level config: the system handles per-scope {@code MechanicsProfile.fixes} configs,
     *  but does nothing when none is set (no fix enabled, nothing to apply). */
    public static FixesSystem install(MinestomMechanics mm) {
        return install(mm, FixesConfig.builder().build());
    }

    /** Installs the fixes system: registers on {@code mm}, wires each fix's manager, installs the event node. */
    public static FixesSystem install(MinestomMechanics mm, FixesConfig cfg) {
        FixesSystem system = new FixesSystem(mm, cfg);
        mm.registerFixes(system);
        system.legacyArrowVisibility.install(system.node);
        // Block-placement fixes override a server-wide packet listener, so they read the install config directly (cannot
        // vary per scope). Two toggles share the one listener: the chunk-resend sync fix, and the 1.8 self-placement
        // compat (place a block into your own body). SelfPlacementFix wraps the corrected listener, so enabling it gives
        // the chunk fix too; otherwise install the listener bare. Temporary, until the upstream Minestom chunk fix is on
        // the pinned dependency (then SelfPlacementFix repoints at the upstream listener and BlockPlacementFix is gone).
        boolean chunkSync = cfg.blockPlacement() != null && Boolean.TRUE.equals(cfg.blockPlacement().enabled());
        boolean selfPlace = cfg.selfPlacement() != null && Boolean.TRUE.equals(cfg.selfPlacement().enabled());
        if (selfPlace) SelfPlacementFix.install();
        else if (chunkSync) BlockPlacementFix.install();
        mm.install(system.node);
        return system;
    }
}
