package io.github.term4.minestommechanics.util.tick;

import net.kyori.adventure.key.Key;
import net.minestom.server.ServerFlag;
import net.minestom.server.coordinate.Vec;
import org.jetbrains.annotations.Nullable;

/**
 * Scales tick-based values to the live server TPS. Both baselines default to {@link TickScalingConfig#SERVER_TPS}
 * (track the server TPS -> <b>no scaling</b>); opt in with a fixed baseline. Durations scale against {@code referenceTps}
 * (per-scope and per-module, a system's {@code KEY}); client-coupled physics + velocity against {@code clientTps}.
 * Identity whenever a baseline equals the live server TPS.
 */
public final class TickScaler {

    private TickScaler() {}

    /** Global-scope scaling, refreshed from the global profile; physics + static-context durations read it. */
    private static volatile TickScalingConfig global = TickScalingConfig.DEFAULTS;

    /** Refreshes the global baselines; called when the global profile's scaling changes. */
    public static void setGlobal(@Nullable TickScalingConfig cfg) {
        global = cfg != null ? cfg : TickScalingConfig.DEFAULTS;
    }

    /** Live server tick rate (we read it, never set it). */
    private static int serverTps() { return ServerFlag.SERVER_TICKS_PER_SECOND; }

    /** Resolves a baseline: the {@link TickScalingConfig#SERVER_TPS} sentinel means "track the server TPS" (-> no scaling). */
    private static int resolve(int baseline) {
        return baseline == TickScalingConfig.SERVER_TPS ? serverTps() : baseline;
    }

    /** {@code module}'s duration in server ticks, from the resolved scope config ({@code null} = defaults). */
    public static int duration(int baseTicks, @Nullable TickScalingConfig cfg, Key module) {
        int ref = resolve((cfg != null ? cfg : TickScalingConfig.DEFAULTS).referenceTps(module));
        return scaleDuration(baseTicks, serverTps(), ref);
    }

    /** {@code module}'s duration from the global scope (static / entity-less contexts). */
    public static int duration(int baseTicks, Key module) {
        return scaleDuration(baseTicks, serverTps(), resolve(global.referenceTps(module)));
    }

    /** Pure core: {@code round(baseTicks × serverTps / referenceTps)}. */
    static int scaleDuration(int baseTicks, int serverTps, int referenceTps) {
        return Math.round(baseTicks * (float) serverTps / referenceTps);
    }

    /** An "every N client-ticks" cadence in server ticks ({@code × serverTps/clientTps}, min 1): e.g. a broadcast throttle. Literal at the default. */
    public static int clientCadence(int baseTicks) {
        return Math.max(1, Math.round(baseTicks * (float) serverTps() / resolve(global.clientTps())));
    }

    /** {@code clientTps / serverTps} - the per-server-tick physics fraction (1 at the default). */
    private static double physicsScale() { return resolve(global.clientTps()) / (double) serverTps(); }

    /** Per-server-tick drag so {@code drag^elapsed} matches the clientTps-rate decay (exact). Identity at the default. */
    public static double dragPerTick(double drag) { return Math.pow(drag, physicsScale()); }

    /** Re-rates a blocks/tick velocity value (impulse, cap, set-value) to the server rate. Identity at the default. */
    public static double impulse(double valueBt) { return valueBt * physicsScale(); }

    /** Per-server-tick gravity for a blocks/tick velocity: {@code gravity × (clientTps/serverTps)²}. Identity at the default. */
    public static double gravityPerTick(double gravity) { double s = physicsScale(); return gravity * s * s; }

    /** Re-rates a server b/t velocity to the client's b/t for a velocity packet ({@code × serverTps/clientTps}). Identity at the default. */
    public static Vec toClientVelocity(Vec serverBt) {
        return serverBt.mul(serverTps() / (double) resolve(global.clientTps()));
    }

    /** Inverse of {@link #toClientVelocity}: a client/vanilla b/t velocity to the server rate. Identity at the default. */
    public static Vec fromClientVelocity(Vec clientBt) {
        return clientBt.mul(resolve(global.clientTps()) / (double) serverTps());
    }
}
