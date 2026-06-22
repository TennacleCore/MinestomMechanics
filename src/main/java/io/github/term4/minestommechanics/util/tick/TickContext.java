package io.github.term4.minestommechanics.util.tick;

import net.minestom.server.instance.Instance;

/** What a {@link Tickable} receives each tick: the instance, its just-advanced {@link TickSystem} tick, and live server TPS. */
public record TickContext(Instance instance, long tick, int serverTps) {}
