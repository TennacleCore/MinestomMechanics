package io.github.term4.minestommechanics;

/**
 * Marker for an installable mechanics system held in the {@link MinestomMechanics} registry. A system registers itself
 * with {@link MinestomMechanics#register} (typically from its static {@code install}) and is looked up by its concrete
 * type with {@link MinestomMechanics#module}. Implementing this is all a new system needs to be first-class in the
 * registry — no per-system field or accessor on {@code MinestomMechanics}.
 */
public interface MechanicsModule {
}
