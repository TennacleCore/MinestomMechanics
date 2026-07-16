package io.github.term4.minestommechanics.mechanics.hunger;

/**
 * The cost rule for one exhaustion source: maps the source's reported quantity (a heal event's 1, the fast regen's
 * spent saturation, a custom ability's own value) to the exhaustion charged. No config entry = {@link #dynamic()}.
 */
@FunctionalInterface
public interface ExhaustionCost {

    float cost(float quantity);

    /** A fixed cost per event, ignoring the quantity. */
    static ExhaustionCost flat(float value) { return q -> value; }

    /** The quantity times {@code scale} - keeps a dynamic source dynamic while tuning it. */
    static ExhaustionCost scaled(float scale) { return q -> q * scale; }

    /** The quantity as-is (the default for unconfigured sources). */
    static ExhaustionCost dynamic() { return q -> q; }

    /** Never charges. */
    static ExhaustionCost free() { return q -> 0f; }
}
