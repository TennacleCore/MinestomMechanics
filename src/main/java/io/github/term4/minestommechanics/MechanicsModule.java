package io.github.term4.minestommechanics;

import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import org.jetbrains.annotations.Nullable;

/**
 * Marker for an installable system in the {@link MinestomMechanics} registry: registered via
 * {@link MinestomMechanics#register} (typically from a static {@code install}) and looked up by concrete type via
 * {@link MinestomMechanics#module} - no per-system field or accessor on the core.
 */
public interface MechanicsModule {

    /** The system's installed event node; {@code register} detaches the replaced module's so a re-install never stacks listeners. */
    default @Nullable EventNode<? extends Event> node() { return null; }
}
