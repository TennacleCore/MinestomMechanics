package io.github.term4.minestommechanics.util.tick;

import net.minestom.server.MinecraftServer;
import net.minestom.server.ServerFlag;
import net.minestom.server.entity.Entity;
import net.minestom.server.event.EventFilter;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.instance.InstanceTickEvent;
import net.minestom.server.event.instance.InstanceUnregisterEvent;
import net.minestom.server.event.trait.InstanceEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.timer.TaskSchedule;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * The library's tick system in one place: the server-wide clock, the per-instance clocks, and the per-tick update loop.
 * {@link #serverTick()} is a server-wide counter for state no instance owns; {@link #instanceTick(Instance)} advances once
 * per {@link InstanceTickEvent} and backs everything timed per entity/world (the two run on different phases, so a value
 * stamped on one clock must not be judged against the other). Per-tick work installs as a {@link Tickable}
 * ({@link #register}); each instance tick advances its clock then dispatches in {@link TickPhase} + registration order,
 * gated by interval. {@link #start()} once at init.
 */
public final class TickSystem {

    private TickSystem() {}

    private static final AtomicBoolean STARTED = new AtomicBoolean();

    /** Server-wide tick counter. */
    private static final AtomicLong serverTick = new AtomicLong();
    /** Per-instance tick counters (removed when an instance unregisters). */
    private static final Map<Instance, AtomicLong> CLOCKS = new ConcurrentHashMap<>();
    /** Registered work, bucketed by phase; insertion order within a phase. */
    private static final Map<TickPhase, List<Tickable>> BY_PHASE = new EnumMap<>(TickPhase.class);

    static { for (TickPhase p : TickPhase.values()) BY_PHASE.put(p, new CopyOnWriteArrayList<>()); }

    /** Starts the clocks and the dispatch loop. Idempotent; call once at startup. */
    public static void start() {
        if (!STARTED.compareAndSet(false, true)) return;
        MinecraftServer.getSchedulerManager()
                .buildTask(serverTick::incrementAndGet).repeat(TaskSchedule.tick(1)).schedule();
        // Clock + cleanup on a typed INSTANCE node rather than raw global listeners, mounted on the global handler.
        EventNode<InstanceEvent> node = EventNode.type("mm:tick", EventFilter.INSTANCE);
        node.addListener(InstanceTickEvent.class, e -> { advance(e.getInstance()); dispatch(e.getInstance()); });
        node.addListener(InstanceUnregisterEvent.class, e -> CLOCKS.remove(e.getInstance()));
        MinecraftServer.getGlobalEventHandler().addChild(node);
    }

    /** The server-wide tick (state not owned by any one instance). */
    public static long serverTick() { return serverTick.get(); }

    /** The instance's current tick ({@code 0} if it has not ticked yet). */
    public static long instanceTick(@Nullable Instance instance) {
        if (instance == null) return 0L;
        AtomicLong c = CLOCKS.get(instance);
        return c != null ? c.get() : 0L;
    }

    /** The tick of the entity's instance ({@code 0} if unspawned). */
    public static long instanceTick(@Nullable Entity entity) {
        return entity == null ? 0L : instanceTick(entity.getInstance());
    }

    /** Installs a {@link Tickable}; it ticks from the next instance tick on. */
    public static void register(Tickable tickable) {
        BY_PHASE.get(tickable.phase()).add(tickable);
    }

    /** Installs a lambda in {@code phase} at every-tick cadence. */
    public static void register(TickPhase phase, Consumer<TickContext> fn) {
        register(new Tickable() {
            @Override public void tick(TickContext ctx) { fn.accept(ctx); }
            @Override public TickPhase phase() { return phase; }
        });
    }

    private static void advance(Instance instance) {
        CLOCKS.computeIfAbsent(instance, k -> new AtomicLong()).incrementAndGet();
    }

    private static void dispatch(Instance instance) {
        long tick = instanceTick(instance);
        TickContext ctx = new TickContext(instance, tick, ServerFlag.SERVER_TICKS_PER_SECOND);
        for (TickPhase phase : TickPhase.values()) {
            for (Tickable t : BY_PHASE.get(phase)) {
                int iv = t.interval();
                if (iv <= 1 || tick % iv == 0) t.tick(ctx);
            }
        }
    }
}
