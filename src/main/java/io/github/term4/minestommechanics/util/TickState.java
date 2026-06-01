package io.github.term4.minestommechanics.util;

/** Event at eventTick, effective for duration ticks. Use isActiveWithin(ticks) when duration is passed at check time. */
public record TickState(long eventTick, int duration) {

    /** True if {@code now < eventTick + duration}. */
    public boolean isActive() {
        return TickClock.now() < eventTick + duration;
    }

    /** True if event occurred within last {@code ticks} */
    public boolean isActiveWithin(int ticks) {
        return (TickClock.now() - eventTick) <= ticks;
    }

    /** True if event was more than {@code ticks} ago (e.g. "on ground in past N" = last airborne was stale). */
    public boolean isStaleAfter(int ticks) {
        return (TickClock.now() - eventTick) > ticks;
    }

    public int remainingTicks() {
        return (int) Math.max(0, eventTick + duration - TickClock.now());
    }
}
