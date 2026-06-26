package io.github.term4.minestommechanics.platform.fixes.client;

import net.minestom.server.instance.Instance;
import net.minestom.server.network.player.ClientSettings;
import org.jetbrains.annotations.Nullable;

/**
 * View-distance clamp: caps a client's reported view distance to the instance's before Minestom processes a settings
 * change, so {@code Player#refreshSettings} never over-sends chunks.
 *
 * <p><b>Why:</b> Minestom's {@code refreshSettings} loads/unloads chunks on a view-distance change using the RAW client
 * view distance, while spawn and movement use the effective range ({@code min(viewDistance, instanceViewDistance) + 1}).
 * A client requesting more than the instance's view distance is then sent chunks beyond the cap that the movement/unload
 * path never manages - they stay loaded for the whole session and render as a broken "invisibility band" far from spawn
 * on legacy clients (placed blocks, entities and even the player itself go invisible there). It also double-sends the
 * boundary ring. Clamping the reported view distance to the instance's keeps the change within the managed range.
 *
 * <p>Applied to ALL clients and deliberately NOT gated on protocol version: resolving a client's protocol races the
 * join, and the clamp is a no-op for any client already within the cap (which includes modern clients, clamped to the
 * server view distance on join). Hooked from
 * {@link io.github.term4.minestommechanics.platform.player.OptimizedPlayer#refreshSettings}.
 *
 * <p><b>Temporary:</b> this is the server-side workaround. The proper fix patches Minestom's {@code refreshSettings} to
 * use the effective bounds (upstream branch {@code fix/refresh-settings-view-distance}); once that is merged and on the
 * pinned Minestom dependency, this fix can be removed.
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
