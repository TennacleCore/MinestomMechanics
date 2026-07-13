package io.github.term4.minestommechanics.tracking;

import io.github.term4.minestommechanics.util.tick.TickSystem;
import io.github.term4.minestommechanics.util.tick.TickState;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.Player;
import net.minestom.server.event.EventFilter;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.PlayerSpawnEvent;
import net.minestom.server.event.player.PlayerStartSprintingEvent;
import net.minestom.server.event.player.PlayerStopSprintingEvent;
import net.minestom.server.event.trait.PlayerEvent;
import net.minestom.server.tag.Tag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicBoolean;

// Sprint usage audited: combat/knockback all routes through this tracker (KnockbackCalculator, KnockbackConfigResolver,
// Vanilla18.sprintingForKb, VelocityContext); raw isSprinting() is only the no-tracker fallback. MotionTracker's raw
// reads (sprint-jump boost, fluid friction) are physics, not combat timing, so they stay raw by design.
public final class SprintTracker implements Tracker {

    private static final Tag<TickState> LAST_SPRINT_STATE = Tag.Transient("mm:last-sprint-state");
    private static final Tag<TickState> LAST_CLIENT_START_SPRINT = Tag.Transient("mm:client-sprint-start");
    private static final Tag<TickState> LAST_CLIENT_STOP_SPRINT  = Tag.Transient("mm:client-sprint-stop");
    private static final AtomicBoolean CLOCK_RESET = new AtomicBoolean();

    public SprintTracker() {
        if (CLOCK_RESET.compareAndSet(false, true)) {
            TickSystem.onClockChange(e -> {
                if (e instanceof Player p) clearTransient(p);
            });
        }
    }

    public void markStopSprint(Player player) {
        player.setTag(LAST_SPRINT_STATE, new TickState(TickSystem.tick(player), 0));
    }

    /** Listener node that stamps the sprint start/stop ticks. */
    @Override
    public EventNode<@NotNull PlayerEvent> node() {
        EventNode<@NotNull PlayerEvent> node = EventNode.type("mm:sprint-tracker", EventFilter.PLAYER);

        node.addListener(PlayerStartSprintingEvent.class, e -> {
            Player p = e.getPlayer();
            p.setTag(LAST_CLIENT_START_SPRINT, new TickState(TickSystem.tick(p), 0));
        });

        node.addListener(PlayerStopSprintingEvent.class, e -> {
            Player p = e.getPlayer();
            markStopSprint(p);
            p.setTag(LAST_CLIENT_STOP_SPRINT, new TickState(TickSystem.tick(p), 0));
        });

        // Instance change reseeds the per-instance clock these stamps use; isClientSprinting compares raw eventTicks
        // (not covered by the TickState future-guard), so drop them on (re)spawn to avoid a cross-instance misread.
        node.addListener(PlayerSpawnEvent.class, e -> clearTransient(e.getPlayer()));

        return node;
    }

    /** Drops the per-instance sprint stamps; their {@link TickSystem} baseline is meaningless after an instance change. */
    public static void clearTransient(Player p) {
        p.removeTag(LAST_SPRINT_STATE);
        p.removeTag(LAST_CLIENT_START_SPRINT);
        p.removeTag(LAST_CLIENT_STOP_SPRINT);
    }

    /** Returns true if a player was sprinting within {@code ticks}, returns raw sprint state if tracker doesn't exist */
    public static boolean wasRecentlySprinting(@Nullable SprintTracker t, Entity e, long ticks) {
        if (!(e instanceof Player p)) return e.isSprinting();
        if (t == null || p.isSprinting()) return p.isSprinting();
        TickState state = p.getTag(LAST_SPRINT_STATE);
        if (state == null) return false;
        return state.isActiveWithin(TickSystem.tick(p), (int) ticks);
    }

    /** True if the client's last sprint action was start (client thinks it is currently sprinting even if the server does not.) */
    public static boolean isClientSprinting(@Nullable SprintTracker t, Entity e) {
        if (t == null) return e.isSprinting();
        TickState start = e.getTag(LAST_CLIENT_START_SPRINT);
        TickState stop  = e.getTag(LAST_CLIENT_STOP_SPRINT);
        if (start == null) return e.isSprinting();  // fallback to server state
        if (stop == null) return true;
        return start.eventTick() > stop.eventTick();
    }

    /** True if client was sprinting within last {@code ticks} */
    public static boolean wasClientRecentlySprinting(@Nullable SprintTracker t, Entity e, int ticks) {
        if (t == null) return e.isSprinting();
        if (isClientSprinting(t, e)) return true;
        TickState stop = e.getTag(LAST_CLIENT_STOP_SPRINT);
        // Not currently sprinting (per the check above) and no stop ever recorded = never sprinted at all
        // (e.g. fresh join) - not "recently sprinting".
        if (stop == null) return false;
        return stop.isActiveWithin(TickSystem.tick(e), ticks);
    }
}
