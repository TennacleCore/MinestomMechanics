package io.github.term4.minestommechanics.tracking;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Protocol-version detection: parses the ViaVersion proxy-details JSON to a protocol number and classifies it as legacy
 * ({@code <= 1.8.x}) or modern. The per-player store + plugin-message routing live in {@link ClientInfoTracker}; this is
 * the stateless version logic it (and the compat/reach layer) delegate to.
 */
public final class ClientVersion {

    /** Returned until the proxy-details plugin message arrives (it lands shortly after login, not during the join events). */
    public static final int UNKNOWN_PROTOCOL = -1;
    /** Highest protocol version still treated as legacy (1.8.x = 47). */
    public static final int LEGACY_PROTOCOL_MAX = 47;

    private ClientVersion() {}

    /** Parses the protocol from the ViaVersion proxy-details JSON ({@code {"version": <protocol>, ...}}), or {@link #UNKNOWN_PROTOCOL}. */
    public static int parse(String json) {
        if (json == null || json.isEmpty()) return UNKNOWN_PROTOCOL;
        try {
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            var version = obj.get("version");
            return (version != null && version.isJsonPrimitive()) ? version.getAsInt() : UNKNOWN_PROTOCOL;
        } catch (Exception ignored) {
            return UNKNOWN_PROTOCOL;
        }
    }

    /** Whether {@code protocol} is a known legacy ({@code <= 1.8.x}) client ({@link #UNKNOWN_PROTOCOL} = not legacy). */
    public static boolean isLegacy(int protocol) {
        return protocol != UNKNOWN_PROTOCOL && protocol <= LEGACY_PROTOCOL_MAX;
    }
}
