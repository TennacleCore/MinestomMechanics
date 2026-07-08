package io.github.term4.minestommechanics.platform.fixes.client;

import net.minestom.server.entity.EquipmentSlot;
import net.minestom.server.item.ItemStack;
import io.github.term4.minestommechanics.platform.PacketShapes;
import net.minestom.server.network.packet.server.SendablePacket;
import net.minestom.server.network.packet.server.ServerPacket;
import net.minestom.server.network.packet.server.play.EntityEquipmentPacket;

import java.util.EnumMap;
import java.util.Map;

/**
 * Strips empty (AIR) slots from outgoing {@code EntityEquipmentPacket}s (vanilla parity - {@code ServerEntity} skips
 * them). Minestom sends every slot incl. {@code BODY=AIR}; ViaBackwards remaps BODY onto the chestplate slot, and
 * {@code Map.copyOf} order decides which entry wins - the real chestplate intermittently renders invisible on legacy
 * clients. Applied to ALL clients: it's what vanilla does, and a legacy gate leaked through the join window (existing
 * players' equipment is sent before the viewer's protocol resolves). A single-slot AIR update (clearing) is untouched.
 * Temporary: upstream fix on branch {@code fix/skip-empty-equipment-slots}; remove once on the pinned Minestom.
 */
public final class LegacyEquipmentFix {

    /** {@code true} once installed/enabled (set by {@code FixesSystem}); {@code false} = the fix is off. */
    private static volatile boolean enabled;

    private LegacyEquipmentFix() {}

    /** Enables the fix. Called by {@code FixesSystem.install} when the install config turns it on. */
    public static void install() {
        enabled = true;
    }

    /** Strips empty equipment slots from an {@code EntityEquipmentPacket}; otherwise returns {@code packet} unchanged. */
    public static SendablePacket rewrite(SendablePacket packet) {
        if (!enabled) return packet;
        final ServerPacket serverPacket = PacketShapes.unwrapStateless(packet);
        if (!(serverPacket instanceof EntityEquipmentPacket equipment)) return packet;

        final Map<EquipmentSlot, ItemStack> kept = new EnumMap<>(EquipmentSlot.class);
        for (Map.Entry<EquipmentSlot, ItemStack> entry : equipment.equipments().entrySet()) {
            if (!entry.getValue().isAir()) kept.put(entry.getKey(), entry.getValue());
        }
        // No empty slots to drop, or all empty (can't build an empty packet, and a single-slot AIR is a legit slot-clear).
        if (kept.size() == equipment.equipments().size() || kept.isEmpty()) return packet;
        return new EntityEquipmentPacket(equipment.entityId(), kept);
    }
}
