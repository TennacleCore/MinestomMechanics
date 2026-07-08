package io.github.term4.minestommechanics.platform.fixes.client;

import net.minestom.server.instance.Instance;
import net.minestom.server.network.player.ClientSettings;
import org.jetbrains.annotations.Nullable;

/**
 * Caps a client's reported view distance to the instance's before {@code refreshSettings} processes it. Minestom
 * loads/unloads on the RAW view distance while spawn/movement use the effective range, so chunks past the cap are
 * sent once and never managed - the legacy-client "invisibility band" far from spawn (+ a double-sent boundary
 * ring). Applied to ALL clients: protocol resolution races the join, and the clamp is a no-op within the cap.
 * Temporary: upstream fix on branch {@code fix/refresh-settings-view-distance}; remove once on the pinned Minestom.
 */
public final class LegacyViewDistanceFix {

    /** {@code true} once installed/enabled (set by {@code FixesSystem}); {@code false} = the fix is off. */
    private static volatile boolean enabled;

    private LegacyViewDistanceFix() {}

    /** Enables the fix. Called by {@code FixesSystem.install} when the install config turns it on. */
    public static void install() {
        enabled = true;
    }

    /** Returns {@code settings} with its view distance clamped to {@code instance}'s; unchanged when off, instance-less, or already within the cap. */
    public static ClientSettings clamp(@Nullable Instance instance, ClientSettings settings) {
        if (!enabled || instance == null || settings.viewDistance() <= instance.viewDistance()) return settings;
        return new ClientSettings(settings.locale(), (byte) instance.viewDistance(),
                settings.chatMessageType(), settings.chatColors(), settings.displayedSkinParts(),
                settings.mainHand(), settings.enableTextFiltering(), settings.allowServerListings(),
                settings.particleSetting());
    }
}
