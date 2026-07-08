package io.github.term4.minestommechanics.vri;

import io.github.term4.minestommechanics.MinestomMechanics;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import org.jetbrains.annotations.NotNull;

/**
 * VRI (Vanilla Re-Implemented): world behaviors Minestom omits - crack overlay, block drops, item pickup/drop
 * (chests, deaths later). Server-wide, not profile-scoped; drop spawns fire {@link ItemSpawnEvent}. Break FX
 * (world event 2001) is native in {@code breakBlock} - don't re-add it.
 */
public final class Vri {

    private Vri() {}

    public static void install(@NotNull MinestomMechanics mm, @NotNull VriConfig config) {
        EventNode<@NotNull Event> node = EventNode.all("mm:vri");
        if (config.blockBreakProgress) BlockBreakProgress.install(node);
        if (config.blockDrops != null) BlockDrops.install(node, config.blockDrops, config.itemPhysics);
        if (config.itemPickup) ItemPickup.install(node);
        if (config.itemDrop) ItemDrop.install(node, config.itemPhysics);
        mm.install(node);
    }
}
