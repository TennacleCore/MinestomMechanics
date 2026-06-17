package io.github.term4.minestommechanics.tracking.motion;

import net.minestom.server.ServerFlag;
import net.minestom.server.coordinate.Vec;

/**
 * Snaps an outgoing velocity (b/s) onto the legacy 1.8 wire grid. 1.8 encodes velocity as {@code (int)(motPerTick*8000)}
 * shorts (truncating), but the modern pipeline + ViaVersion can round a value one short high for legacy clients.
 * {@link #snap} re-centres each component in vanilla's truncation bucket so it survives quantization and lands on the
 * vanilla short. A server-emulation concern, toggled via {@code KnockbackConfig.quantizeVelocity}.
 */
public final class LegacyVelocity {

    /** Vanilla 1.8 wire scale: shorts per block-per-tick. */
    private static final double WIRE_SCALE = 8000.0;
    /** Vanilla per-component clamp ({@code PacketPlayOutEntityVelocity}): {@code +-3.9} b/t. */
    private static final double WIRE_CLAMP = 3.9;

    private LegacyVelocity() {}

    /** Snaps a velocity in blocks/second onto the vanilla 1.8 wire grid (see class doc). */
    public static Vec snap(Vec perSecond) {
        double tps = ServerFlag.SERVER_TICKS_PER_SECOND;
        return new Vec(
                snapAxis(perSecond.x() / tps) * tps,
                snapAxis(perSecond.y() / tps) * tps,
                snapAxis(perSecond.z() / tps) * tps);
    }

    /** One component in b/t: vanilla clamp, vanilla {@code (int)} truncation, re-encode. */
    private static double snapAxis(double bt) {
        bt = Math.max(-WIRE_CLAMP, Math.min(WIRE_CLAMP, bt));
        int shorts = (int) (bt * WIRE_SCALE); // vanilla cast: truncation toward zero
        if (shorts == 0) return 0;
        return (shorts + Math.copySign(0.25, shorts)) / WIRE_SCALE;
    }
}
