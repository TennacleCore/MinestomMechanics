package io.github.term4.minestommechanics.tracking;

import io.github.term4.minestommechanics.platform.compatibility.AnimatiumFeature;
import io.github.term4.minestommechanics.platform.player.OptimizedPlayer;
import net.minestom.server.entity.Player;
import net.minestom.server.tag.Tag;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A per-player view of client-side info: the built-in {@link #protocol()} version and Animatium status, plus a typed
 * store ({@link #get} / {@link #set} keyed by {@link ClientKey}) for whatever the end user wants to attach. The library
 * puts nothing mod-specific here - it's where a consumer records e.g. the login version or Animatium use, then persists
 * to their own database on disconnect.
 *
 * <p>Obtained from {@code mm.client(player)}; a fresh lightweight view each call (state lives on the player). Custom
 * values use a transient player tag, so they drop when the player disconnects.
 */
public final class ClientProfile {

    /** Custom end-user data, keyed by {@link ClientKey}; transient so it drops with the player. */
    private static final Tag<Map<ClientKey<?>, Object>> DATA = Tag.Transient("mm:client-data");

    private final Player player;
    private final ClientInfoTracker tracker;

    ClientProfile(Player player, ClientInfoTracker tracker) {
        this.player = player;
        this.tracker = tracker;
    }

    public Player player() { return player; }

    /** The player's protocol version (e.g. {@code 47} = 1.8), or {@link ClientVersion#UNKNOWN_PROTOCOL} until the proxy/Via handshake resolves it. */
    public int protocol() { return tracker.getProtocol(player); }

    /** Whether this player connected with an Animatium client (sent the {@code animatium:info} handshake). */
    public boolean isAnimatium() {
        return player instanceof OptimizedPlayer op && op.compat().isAnimatiumClient();
    }

    /** The Animatium features this client applies natively (empty for non-Animatium clients). */
    public Set<AnimatiumFeature> animatiumFeatures() {
        return player instanceof OptimizedPlayer op ? op.compat().nativeFeatures() : Set.of();
    }

    /** The value stored under {@code key}, or {@code null} if unset. */
    @SuppressWarnings("unchecked")
    public <T> @Nullable T get(ClientKey<T> key) {
        Map<ClientKey<?>, Object> data = player.getTag(DATA);
        return data != null ? (T) data.get(key) : null;
    }

    /** Stores (or with {@code null} removes) {@code value} under {@code key}. */
    public <T> void set(ClientKey<T> key, @Nullable T value) {
        Map<ClientKey<?>, Object> data = player.getTag(DATA);
        if (data == null) {
            synchronized (player) {
                data = player.getTag(DATA);
                if (data == null) {
                    data = new ConcurrentHashMap<>();
                    player.setTag(DATA, data);
                }
            }
        }
        if (value == null) data.remove(key); else data.put(key, value);
    }
}
