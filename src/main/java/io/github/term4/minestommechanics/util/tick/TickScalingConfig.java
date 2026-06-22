package io.github.term4.minestommechanics.util.tick;

import net.kyori.adventure.key.Key;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-scope TPS-scaling config (rides the {@code MechanicsProfile} chain). The default baseline {@link #SERVER_TPS}
 * tracks the live server TPS, so nothing scales: durations stay literal ticks and physics runs native, like a vanilla
 * {@code /tick rate}. Opt in with a fixed baseline (e.g. {@code referenceTps}/{@code clientTps} = 20): values authored
 * at it then stretch to the live TPS via {@link TickScaler}. A module - a system's {@code KEY}, or any custom
 * {@link Key} - may override {@code referenceTps}.
 */
public record TickScalingConfig(int referenceTps, int clientTps, Map<Key, Integer> moduleReferenceTps) {

    /** Baseline sentinel: track the live server TPS, leaving the scaled quantity literal/native (the no-scaling default). */
    public static final int SERVER_TPS = 0;

    /** No scaling: both baselines follow the live server TPS (literal durations, native physics). The opt-out default. */
    public static final TickScalingConfig DEFAULTS = new TickScalingConfig(SERVER_TPS, SERVER_TPS, Map.of());

    public TickScalingConfig {
        if (referenceTps < SERVER_TPS || clientTps < SERVER_TPS)
            throw new IllegalArgumentException("tps must be >= 0 (0 = follow server, the no-scaling default)");
        moduleReferenceTps = Map.copyOf(moduleReferenceTps);
    }

    /** The baseline for {@code module}: its override, else the scope default (either may be {@link #SERVER_TPS}). */
    public int referenceTps(Key module) {
        Integer o = moduleReferenceTps.get(module);
        return o != null ? o : referenceTps;
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private int referenceTps = SERVER_TPS;
        private int clientTps = SERVER_TPS;
        private final Map<Key, Integer> moduleRefs = new HashMap<>();

        /** Server-authoritative duration baseline (i-frames, cooldowns, lifetimes). {@link #SERVER_TPS} (default) = no scaling (literal ticks). */
        public Builder referenceTps(int v) { referenceTps = v; return this; }

        /** Per-module duration baseline, overriding the default (e.g. {@code referenceTps(KnockbackSystem.KEY, 20)}). */
        public Builder referenceTps(Key module, int v) {
            if (v < SERVER_TPS) throw new IllegalArgumentException("tps must be >= 0 (0 = follow server)");
            moduleRefs.put(module, v);
            return this;
        }

        /**
         * Client-coupled physics + velocity baseline. {@link #SERVER_TPS} (default) = native physics; set to a client's
         * prediction rate (20 for vanilla clients) to keep predicted movement synced - any other value desyncs it.
         */
        public Builder clientTps(int v) { clientTps = v; return this; }

        public TickScalingConfig build() { return new TickScalingConfig(referenceTps, clientTps, moduleRefs); }
    }
}
