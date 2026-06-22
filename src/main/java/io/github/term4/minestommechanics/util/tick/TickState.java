package io.github.term4.minestommechanics.util.tick;

/**
 * Event at eventTick, effective for duration ticks. The no-arg methods read the server-wide clock
 * ({@link TickSystem#serverTick()}); the {@code (long now)} overloads take the clock explicitly, so callers can evaluate
 * a window against the per-instance {@link TickSystem} clock instead. Use isActiveWithin(ticks) when duration is passed at check time.
 */
public record TickState(long eventTick, int duration) {

    /** True if {@code now < eventTick + duration}. */
    public boolean isActive() {
        return isActive(TickSystem.serverTick());
    }

    /**
     * True if {@code eventTick <= now < eventTick + duration} against the supplied clock. A "future" stamp
     * ({@code now < eventTick}) reads inactive - guards a stamp made under a different clock baseline (e.g. one carried
     * across instances by the per-instance {@link TickSystem} clock).
     */
    public boolean isActive(long now) {
        return now >= eventTick && now < eventTick + duration;
    }

    /** True if event occurred within last {@code ticks} */
    public boolean isActiveWithin(int ticks) {
        return isActiveWithin(TickSystem.serverTick(), ticks);
    }

    /** True if the event was within the last {@code ticks} against the supplied clock; a "future" stamp reads not-recent (see {@link #isActive(long)}). */
    public boolean isActiveWithin(long now, int ticks) {
        long elapsed = now - eventTick;
        return elapsed >= 0 && elapsed <= ticks;
    }

    /** True if event was more than {@code ticks} ago (e.g. "on ground in past N" = last airborne was stale). */
    public boolean isStaleAfter(int ticks) {
        return isStaleAfter(TickSystem.serverTick(), ticks);
    }

    /** True if event was more than {@code ticks} ago, evaluated against the supplied clock. */
    public boolean isStaleAfter(long now, int ticks) {
        return (now - eventTick) > ticks;
    }

    public int remainingTicks() {
        return remainingTicks(TickSystem.serverTick());
    }

    /** Ticks remaining, evaluated against the supplied clock. */
    public int remainingTicks(long now) {
        return (int) Math.max(0, eventTick + duration - now);
    }
}
