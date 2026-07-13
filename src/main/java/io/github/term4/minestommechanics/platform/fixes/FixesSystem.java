package io.github.term4.minestommechanics.platform.fixes;

import io.github.term4.minestommechanics.MechanicsKeys;
import io.github.term4.minestommechanics.MechanicsModule;
import io.github.term4.minestommechanics.MinestomMechanics;
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
 * Installs the client/protocol behavior fixes from a {@link FixesConfig}. Mirrors the other systems
 * (DamageSystem / ProjectileSystem); per-scope config via {@code FIXES}. Today the only managed fix is the 1.8 legacy
 * arrow-visibility ({@link LegacyArrowVisibility}); future fixes register their own managers here.
 *
 * <p>(The self-meta smoothing fix lives in {@code fixes.client} but its delivery is the custom player override, so it is
 * armed by {@code MetaFix.installListeners()} from {@code MinestomMechanics.init}, not this system.)
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
    /** The 1.8 legacy arrow-visibility manager (runtime on/off + per-player team sync). */
    public LegacyArrowVisibility legacyArrowVisibility() { return legacyArrowVisibility; }

    /** Effective config for {@code subject}: the scoped profile, else the install config. */
    public FixesConfig configFor(@Nullable Entity subject) {
        FixesConfig scoped = mm.profiles().resolve(subject, MechanicsKeys.FIXES);
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

    /**
     * Installs reading the GLOBAL profile's {@link FixesConfig} - which fixes are enabled lives in the profile, like the
     * other systems. The server-wide fixes (equipment / tab-complete / placement) gate from it at install;
     * the event-driven arrow-visibility fix still resolves per-scope. Set the profile before installing.
     */
    public static FixesSystem install(MinestomMechanics mm) {
        FixesConfig global = mm.profiles().resolve(null, MechanicsKeys.FIXES);
        return install(mm, global != null ? global : FixesConfig.builder().build());
    }

    /** Installs from an explicit config (the modular path): registers on {@code mm}, wires each fix's manager, installs the event node. */
    public static FixesSystem install(MinestomMechanics mm, FixesConfig cfg) {
        FixesSystem system = new FixesSystem(mm, cfg);
        mm.register(system);
        system.legacyArrowVisibility.install(system.node);
        // Self-placement overrides the server-wide placement packet listener, so it reads the install config
        // directly (cannot vary per scope). Wraps the STOCK listener; an app that replaces the placement listener
        // (a shard library's world-scoped one) re-installs LAST with that listener as the delegate.
        if (enabled(cfg.selfPlacement())) SelfPlacementFix.install();
        // Equipment-slot fix rides the OptimizedPlayer send-packet override (server-wide), so it reads the install config directly.
        if (enabled(cfg.legacyEquipmentFix())) LegacyEquipmentFix.install();
        // Tab-completion fix replaces the tab-complete packet listener (server-wide), so it reads the install config directly.
        if (enabled(cfg.legacyTabCompleteFix())) LegacyTabCompleteFix.install();
        mm.install(system.node);
        return system;
    }

    private static boolean enabled(@Nullable FixToggle cfg) {
        return cfg != null && Boolean.TRUE.equals(cfg.enabled());
    }
}
