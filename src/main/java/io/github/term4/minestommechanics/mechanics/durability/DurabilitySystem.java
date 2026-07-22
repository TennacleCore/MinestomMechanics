package io.github.term4.minestommechanics.mechanics.durability;

import io.github.term4.minestommechanics.MechanicsKeys;
import io.github.term4.minestommechanics.MechanicsModule;
import io.github.term4.minestommechanics.MinestomMechanics;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EquipmentSlot;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Item durability (damage-on-use). Per-scope config via {@code DURABILITY}.
 *
 * <p><b>Stub:</b> the API surface is in place, but no durability is consumed yet (TODO).
 */
public final class DurabilitySystem implements MechanicsModule {

    private final MinestomMechanics mm;
    private final DurabilityConfig config;
    private final EventNode<@NotNull Event> node;

    public DurabilitySystem(MinestomMechanics mm, DurabilityConfig config) {
        this.mm = mm;
        this.config = config;
        this.node = EventNode.all("mm:durability");
    }

    public EventNode<@NotNull Event> node() { return node; }
    public DurabilityConfig config() { return config; }

    /** Effective config for {@code subject}: the scoped profile (player -&gt; instance -&gt; global), else the install config. */
    public DurabilityConfig configFor(@Nullable Entity subject) {
        DurabilityConfig scoped = mm.profiles().resolve(subject, MechanicsKeys.DURABILITY);
        return scoped != null ? scoped : config;
    }

    /** Active by default; only an explicit {@code enabled(false)} disables. */
    public boolean enabled(@Nullable Entity subject) {
        return !Boolean.FALSE.equals(configFor(subject).enabled());
    }

    /**
     * The combat/mining/Thorns entry point. <b>Stub:</b> a no-op until the durability logic lands.
     */
    public void damage(LivingEntity holder, EquipmentSlot slot, int amount) {
        if (!enabled(holder)) return;
        // TODO(durability): consume Unbreaking, decrement the stack's damage component, break + emit the item on overflow.
    }

    /** Installs the system active (a per-scope {@code MechanicsProfile.durability} config can disable it). */
    public static DurabilitySystem install(MinestomMechanics mm) {
        return install(mm, DurabilityConfig.builder().build());
    }

    public static DurabilitySystem install(MinestomMechanics mm, DurabilityConfig cfg) {
        DurabilitySystem system = new DurabilitySystem(mm, cfg);
        mm.register(system);
        mm.install(system.node);
        return system;
    }
}
