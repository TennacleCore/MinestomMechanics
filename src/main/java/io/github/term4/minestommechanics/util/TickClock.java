package io.github.term4.minestommechanics.util;

import net.minestom.server.MinecraftServer;
import net.minestom.server.timer.TaskSchedule;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Tick based timing for MinestomMechanics
 * Synchronizes all events with server ticks.
 */
public final class TickClock {
    private static final AtomicBoolean STARTED = new AtomicBoolean(false);
    private static final AtomicLong tick = new AtomicLong(0);

    private TickClock() {}

    /** Returns the current server tick */
    public static long now() {
        return tick.get();
    }

    /** Starts the global tick clock */
    public static void start() {
        if (!STARTED.compareAndSet(false, true)) return;
        MinecraftServer.getSchedulerManager()
                .buildTask(tick::incrementAndGet)
                .repeat(TaskSchedule.tick(1))
                .schedule();
    }

}
