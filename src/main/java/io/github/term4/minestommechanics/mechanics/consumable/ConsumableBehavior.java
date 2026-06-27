package io.github.term4.minestommechanics.mechanics.consumable;

import io.github.term4.minestommechanics.mechanics.consumable.ConsumableConfigResolver.ConsumableContext;

/**
 * The pre/during/post hooks of a {@link Consumable} - the open seam for custom consume behavior (the {@code Source.Behavior}
 * / {@code ProjectileBehavior} idiom). Built-ins (golden apples, food, potions) implement {@link #onFinish}; anything beyond
 * effects/hunger (a chorus-fruit teleport, a custom trigger) writes its own. No-op by default; resolved per consume as a {@link ConsumableTypeConfig} knob.
 */
public interface ConsumableBehavior {

    /** A behavior that does nothing (the default). */
    ConsumableBehavior NONE = new ConsumableBehavior() {};

    /** The user started consuming (right-clicked). Pre-consume: stamp state, play a sound, etc. */
    default void onStart(ConsumableContext ctx) {}

    /** Each tick while consuming, with the ticks left before completion. During-consume: progress particles / staged effects. */
    default void onUsing(ConsumableContext ctx, int ticksRemaining) {}

    /** The user finished consuming. Post-consume: apply effects / restore hunger / teleport / etc. (the system consumes the item afterward). */
    default void onFinish(ConsumableContext ctx) {}

    /** The user stopped early (released before completion). Vanilla = nothing happens. */
    default void onCancel(ConsumableContext ctx) {}
}
