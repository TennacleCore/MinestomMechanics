package io.github.term4.minestommechanics.vri;

import io.github.term4.minestommechanics.MechanicsKeys;
import io.github.term4.minestommechanics.MechanicsModule;
import io.github.term4.minestommechanics.MinestomMechanics;
import io.github.term4.minestommechanics.world.WorldPolicy;
import net.minestom.server.entity.Entity;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.entity.EntityItemMergeEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * VRI (Vanilla Re-Implemented): world behaviors Minestom omits - crack overlay, block drops, item pickup/drop
 * (chests, deaths later). Each behavior reads {@link #configFor} per event, so a scope can enable one the install
 * config left off. Drop spawns fire {@link ItemSpawnEvent}. Break FX (world event 2001) is native in
 * {@code breakBlock} - don't re-add it.
 */
public final class Vri implements MechanicsModule {

    private final MinestomMechanics mm;
    private final VriConfig config;
    private final EventNode<@NotNull Event> node;

    private Vri(MinestomMechanics mm, VriConfig config) {
        this.mm = mm;
        this.config = config;
        this.node = EventNode.all("mm:vri");
    }

    @Override public EventNode<@NotNull Event> node() { return node; }

    public VriConfig config() { return config; }

    /** Effective config for {@code subject}: the scoped profile, else the install config. */
    public VriConfig configFor(@Nullable Entity subject) {
        return mm.profiles().resolveOr(subject, MechanicsKeys.VRI, config);
    }

    public static Vri install(@NotNull MinestomMechanics mm, @NotNull VriConfig config) {
        Vri system = new Vri(mm, config);
        // not a toggle: Minestom's item-merge scan is instance-wide (like the pickup scan ItemPickup gates) -
        // co-located items from different worlds would absorb each other
        system.node.addListener(EntityItemMergeEvent.class, e -> {
            if (!WorldPolicy.canAffect(e.getEntity(), e.getMerged())) e.setCancelled(true);
        });
        BlockBreakProgress.install(system.node, system);
        BlockDrops.install(system.node, system);
        ItemPickup.install(system.node, system);
        ItemDrop.install(system.node, system);
        mm.register(system);
        mm.install(system.node);
        return system;
    }
}
