package io.github.term4.minestommechanics;

/**
 * Marker for an installable system in the {@link MinestomMechanics} registry: registered via
 * {@link MinestomMechanics#register} (typically from a static {@code install}) and looked up by concrete type via
 * {@link MinestomMechanics#module} - no per-system field or accessor on the core.
 */
public interface MechanicsModule {
}
