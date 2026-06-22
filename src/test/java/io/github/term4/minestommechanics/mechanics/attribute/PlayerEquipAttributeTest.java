package io.github.term4.minestommechanics.mechanics.attribute;

import io.github.term4.minestommechanics.MechanicsProfile;
import io.github.term4.minestommechanics.mechanics.attribute.catalog.enchant.AquaAffinity;
import io.github.term4.minestommechanics.mechanics.attribute.catalog.enchant.Efficiency;
import io.github.term4.minestommechanics.testsupport.HeadlessServerTest;
import net.minestom.server.component.DataComponents;
import net.minestom.server.entity.EquipmentSlot;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.attribute.Attribute;
import net.minestom.server.entity.attribute.AttributeOperation;
import net.minestom.server.event.EventDispatcher;
import net.minestom.server.event.entity.EntityTickEvent;
import net.minestom.server.event.player.PlayerChangeHeldSlotEvent;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.item.component.EnchantmentList;
import net.minestom.server.item.enchant.Enchantment;
import net.minestom.server.network.ConnectionState;
import net.minestom.server.network.packet.server.SendablePacket;
import net.minestom.server.network.packet.server.play.EntityAttributesPacket;
import net.minestom.server.network.player.GameProfile;
import net.minestom.server.network.player.PlayerConnection;
import net.minestom.server.registry.RegistryKey;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Player equipment attributes are tick-driven, mirroring vanilla's {@code detectEquipmentUpdates}: they apply on the
 * reconcile tick after a change (post-settle), not synchronously on the equip - so a hotbar right-click equip isn't raced
 * by the use-item client prediction (the Aqua Affinity report), and a held swap lags a tick (the attribute-swap window).
 * That window is what {@link AttributeConfig#attributeSwapping} gates: disabled (default) force-refreshes the held push
 * immediately on a hotbar-slot change; permitted leaves it to the tick. Mobs are unaffected (covered by {@link ArmorEnchantTest}).
 */
class PlayerEquipAttributeTest extends HeadlessServerTest {

    private static ItemStack aquaHelmet() {
        return ItemStack.of(Material.DIAMOND_HELMET)
                .with(DataComponents.ENCHANTMENTS, new EnchantmentList(RegistryKey.<Enchantment>unsafeOf(AquaAffinity.KEY), 1));
    }

    private static ItemStack efficiencyPick() {
        return ItemStack.of(Material.DIAMOND_PICKAXE)
                .with(DataComponents.ENCHANTMENTS, new EnchantmentList(RegistryKey.<Enchantment>unsafeOf(Efficiency.KEY), 1));
    }

    /** Aqua Affinity's +4 ADD_MULTIPLIED_TOTAL on submerged_mining_speed (server-side instance). */
    private static boolean hasAqua(LivingEntity e) {
        return e.getAttribute(Attribute.SUBMERGED_MINING_SPEED).modifiers().stream()
                .anyMatch(m -> m.operation() == AttributeOperation.ADD_MULTIPLIED_TOTAL && Math.abs(m.amount() - 4.0) < 1e-9);
    }

    /** Whether Efficiency's mining_efficiency push is present (held source). */
    private static boolean hasMining(LivingEntity e) {
        var inst = e.getAttribute(Attribute.MINING_EFFICIENCY);
        return inst != null && !inst.modifiers().isEmpty();
    }

    /** A joined-state player (viewer of its own inventory, connection in PLAY) recording packets sent to its own client. */
    private static final class Recorded {
        final Player player;
        final List<SendablePacket> sent = new ArrayList<>();

        Recorded() {
            PlayerConnection conn = new PlayerConnection() {
                @Override public void sendPacket(SendablePacket packet) { sent.add(packet); }
                @Override public SocketAddress getRemoteAddress() { return new InetSocketAddress("localhost", 0); }
            };
            conn.setServerState(ConnectionState.PLAY); // onAttributeChanged syncs to self only when state == PLAY
            this.player = new Player(conn, new GameProfile(UUID.randomUUID(), "Tester"));
            this.player.getInventory().addViewer(this.player);
        }

        void permitAttributeSwapping() {
            mm.profiles().setPlayer(player, MechanicsProfile.builder()
                    .attributes(AttributeConfig.builder().attributeSwapping(true).build()).build());
        }

        void tick() { EventDispatcher.call(new EntityTickEvent(player)); }

        /** Whether the client was sent SUBMERGED_MINING_SPEED carrying Aqua Affinity's +4 push. */
        boolean clientGotAqua() {
            return sent.stream()
                    .filter(p -> p instanceof EntityAttributesPacket).map(p -> (EntityAttributesPacket) p)
                    .flatMap(p -> p.properties().stream())
                    .filter(prop -> prop.attribute().equals(Attribute.SUBMERGED_MINING_SPEED))
                    .flatMap(prop -> prop.modifiers().stream())
                    .anyMatch(m -> Math.abs(m.amount() - 4.0) < 1e-9);
        }
    }

    @Test
    void armorEquipAppliesOnTickNotSynchronously() {
        Recorded r = new Recorded();
        r.player.setEquipment(EquipmentSlot.HELMET, aquaHelmet());
        assertFalse(hasAqua(r.player), "a player equip must NOT apply synchronously - the synchronous push races the use-item client prediction");
        r.tick();
        assertTrue(hasAqua(r.player), "the per-tick reconcile applies it (vanilla detectEquipmentUpdates timing)");
    }

    @Test
    void tickReconcileSendsAquaToClient() {
        Recorded r = new Recorded();
        r.player.setEquipment(EquipmentSlot.HELMET, aquaHelmet());
        r.sent.clear(); // drop the equip-time slot packets; we want what the reconcile sends
        r.tick();
        assertTrue(r.clientGotAqua(), "after the reconcile the client receives SUBMERGED_MINING_SPEED(+4) - the thing that makes Aqua actually work client-side");
    }

    @Test
    void heldSwapPatchedByDefaultAppliesImmediately() {
        Recorded r = new Recorded(); // default install config: attributeSwapping unset -> false (patched)
        r.player.getInventory().setItemStack(3, efficiencyPick());
        EventDispatcher.call(new PlayerChangeHeldSlotEvent(r.player, (byte) 0, (byte) 3));
        assertTrue(hasMining(r.player), "patched (default): the held push applies immediately on the slot change, closing the swap window");
    }

    @Test
    void heldSwapLagsWhenSwappingPermitted() {
        Recorded r = new Recorded();
        r.permitAttributeSwapping();
        r.player.getInventory().setItemStack(3, efficiencyPick());
        EventDispatcher.call(new PlayerChangeHeldSlotEvent(r.player, (byte) 0, (byte) 3));
        assertFalse(hasMining(r.player), "swapping permitted: the held push lags the slot change (the exploit window stays open)");
    }
}
