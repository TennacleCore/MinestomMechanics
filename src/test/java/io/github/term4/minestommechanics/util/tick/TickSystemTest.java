package io.github.term4.minestommechanics.util.tick;

import io.github.term4.minestommechanics.testsupport.HeadlessServerTest;
import net.minestom.server.instance.Instance;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * {@link TickSystem} dispatch contract: phase order ({@code PRE_DISPATCH → DEFAULT → POST}) and per-interval gating.
 * Headless has no tick loop, so the instance clock is positioned directly and {@code dispatch} is invoked by reflection
 * (mirroring the rest of the suite). Markers record into a sink; the boot-registered tickables run too but don't touch it.
 */
class TickSystemTest extends HeadlessServerTest {

    private static final List<String> SINK = new CopyOnWriteArrayList<>();

    @BeforeAll
    static void registerMarkers() {
        TickSystem.register(new Marker("A", TickPhase.PRE_DISPATCH, 1));
        TickSystem.register(new Marker("C", TickPhase.POST, 1));
        TickSystem.register(new Marker("B", TickPhase.DEFAULT, 1));   // registered after A/C but runs in DEFAULT order
        TickSystem.register(new Marker("D3", TickPhase.DEFAULT, 3));  // every 3rd tick only
    }

    @BeforeEach
    void clearSink() { SINK.clear(); }

    @Test
    void dispatchesInPhaseOrder() {
        dispatchAt(10); // 10 % 3 != 0 -> D3 skipped
        assertEquals(List.of("A", "B", "C"), SINK);
    }

    @Test
    void intervalGatesByTick() {
        dispatchAt(9); // 9 % 3 == 0 -> D3 fires, in DEFAULT after B
        assertEquals(List.of("A", "B", "D3", "C"), SINK);

        SINK.clear();
        dispatchAt(11); // not divisible by 3
        assertFalse(SINK.contains("D3"), "interval-3 tickable must skip non-multiple ticks");
    }

    private record Marker(String id, TickPhase phase, int interval) implements Tickable {
        @Override public void tick(TickContext ctx) { SINK.add(id); }
    }

    @SuppressWarnings("unchecked")
    private static void dispatchAt(long tick) {
        try {
            Field clocks = TickSystem.class.getDeclaredField("CLOCKS");
            clocks.setAccessible(true);
            ((Map<Instance, AtomicLong>) clocks.get(null)).computeIfAbsent(instance, k -> new AtomicLong()).set(tick);

            Method dispatch = TickSystem.class.getDeclaredMethod("dispatch", Instance.class);
            dispatch.setAccessible(true);
            dispatch.invoke(null, instance);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
