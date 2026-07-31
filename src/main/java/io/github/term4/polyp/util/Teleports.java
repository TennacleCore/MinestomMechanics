package io.github.term4.polyp.util;

import io.github.term4.polyp.Polyp;
import io.github.term4.polyp.tracking.ClientInfoTracker;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.Player;

/**
 * Teleports that don't make a legacy client's proxy invent packets.
 *
 * <p>Minestom's 3-arg {@code teleport} always ORs in {@code DELTA_COORD}, declaring delta movement RELATIVE so the
 * teleport won't drop velocity. A pre-1.21.2 client has no field for that, so ViaBackwards answers it with a
 * synthetic explosion at {@code (0, 20000, 0)} carrying the movement - one per teleport, plus the sound Via emits
 * with it. 1.8 needs none of it: an absolute position packet already zeroes the client's motion
 * ({@code NetHandlerPlayClient.handlePlayerPosLook}), so declaring the delta absolute is both the legacy-correct
 * semantic and two fewer packets.
 */
public final class Teleports {

    private Teleports() {}

    /** {@code flags} are the position/view relative flags; delta movement is chosen by client era. */
    public static void place(Entity entity, Pos target, int flags) {
        ClientInfoTracker clientInfo = Polyp.getInstance().clientInfo();
        if (entity instanceof Player p && clientInfo != null && clientInfo.isLegacy(p)) {
            entity.teleport(target, Vec.ZERO, null, flags);
        } else {
            entity.teleport(target, null, flags);
        }
    }
}
