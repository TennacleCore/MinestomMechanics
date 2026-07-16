package io.github.term4.minestommechanics.platform.fixes.client;

import io.github.term4.minestommechanics.testsupport.HeadlessServerTest;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.network.packet.client.play.ClientClickWindowPacket;
import net.minestom.server.network.packet.client.play.ClientClickWindowPacket.ClickType;
import net.minestom.server.network.packet.server.play.SetCursorItemPacket;
import net.minestom.server.network.packet.server.play.SetPlayerInventorySlotPacket;
import net.minestom.server.network.packet.server.play.WindowItemsPacket;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The remote-slot mirror ({@link InventorySync}): a slot echo the client already predicts is dropped, a genuine change
 * is sent. The headline case is the captured high-lag flicker - a number-key swap out and back nets zero, and every
 * transition echo must be suppressed.
 */
class InventorySyncTest extends HeadlessServerTest {

    private static final ItemStack APPLES = ItemStack.of(Material.GOLDEN_APPLE, 16);

    private static ClientClickWindowPacket click(ClickType type, int wireSlot, int button) {
        return new ClientClickWindowPacket(0, 0, (short) wireSlot, (byte) button, type, Map.of(), ItemStack.Hash.of(ItemStack.AIR));
    }

    @Test
    void netZeroSwapSuppressesEveryEcho() { // the captured bug: tap "1" over an empty slot, then again
        InventorySync sync = new InventorySync();
        assertNotNull(sync.filter(new SetPlayerInventorySlotPacket(0, APPLES)), "anchor: hotbar 0 holds the apples");

        sync.onClick(click(ClickType.SWAP, 35, 0)); // swap main slot 35 <-> hotbar 0
        assertNull(sync.filter(new SetPlayerInventorySlotPacket(35, APPLES)), "predicted -> dropped");
        assertNull(sync.filter(new SetPlayerInventorySlotPacket(0, ItemStack.AIR)), "predicted -> dropped");

        sync.onClick(click(ClickType.SWAP, 35, 0)); // swap back - nets zero
        assertNull(sync.filter(new SetPlayerInventorySlotPacket(35, ItemStack.AIR)), "predicted -> dropped");
        assertNull(sync.filter(new SetPlayerInventorySlotPacket(0, APPLES)), "predicted -> dropped");
    }

    @Test
    void genuineChangeIsSentRedundantIsDropped() {
        InventorySync sync = new InventorySync();
        ItemStack five = ItemStack.of(Material.DIAMOND, 5);
        assertNotNull(sync.filter(new SetPlayerInventorySlotPacket(10, five)), "AIR -> diamonds is a real change");
        assertNull(sync.filter(new SetPlayerInventorySlotPacket(10, five)), "same value again -> redundant, dropped");
        assertNotNull(sync.filter(new SetPlayerInventorySlotPacket(10, five.withAmount(3))), "count change -> sent");
    }

    @Test
    void pickupTracksCursorRoundTrip() {
        InventorySync sync = new InventorySync();
        assertNotNull(sync.filter(new SetPlayerInventorySlotPacket(9, APPLES)), "anchor slot 9");

        sync.onClick(click(ClickType.PICKUP, 9, 0)); // left-click: pick the whole stack up
        assertNull(sync.filter(new SetPlayerInventorySlotPacket(9, ItemStack.AIR)), "slot emptied - predicted");
        assertNull(sync.filter(new SetCursorItemPacket(APPLES)), "cursor holds the stack - predicted");

        sync.onClick(click(ClickType.PICKUP, 9, 0)); // left-click again: place it back
        assertNull(sync.filter(new SetPlayerInventorySlotPacket(9, APPLES)), "slot refilled - predicted");
        assertNull(sync.filter(new SetCursorItemPacket(ItemStack.AIR)), "cursor cleared - predicted");
    }

    @Test
    void throwDecrementsPredictedSlot() {
        InventorySync sync = new InventorySync();
        assertNotNull(sync.filter(new SetPlayerInventorySlotPacket(9, APPLES)), "anchor slot 9");
        sync.onClick(click(ClickType.THROW, 9, 0)); // Q: drop one
        assertNull(sync.filter(new SetPlayerInventorySlotPacket(9, APPLES.withAmount(15))), "one dropped - predicted");
    }

    @Test
    void windowItemsReBaselines() {
        InventorySync sync = new InventorySync();
        List<ItemStack> items = new ArrayList<>(Collections.nCopies(46, ItemStack.AIR));
        items.set(36, APPLES); // window slot 36 = hotbar 0
        assertNotNull(sync.filter(new WindowItemsPacket(0, 0, items, ItemStack.AIR)), "full window always passes");
        assertNull(sync.filter(new SetPlayerInventorySlotPacket(0, APPLES)), "matches the new baseline - dropped");
    }

    @Test
    void leftDragDistributesAndSuppressesResync() {
        InventorySync sync = new InventorySync();
        ItemStack stone = ItemStack.of(Material.STONE, 64);
        assertNotNull(sync.filter(new SetPlayerInventorySlotPacket(9, stone)), "anchor slot 9 = 64 stone");
        sync.onClick(click(ClickType.PICKUP, 9, 0)); // pick the stack onto the cursor
        sync.onClick(click(ClickType.QUICK_CRAFT, -999, 0)); // drag start (left)
        sync.onClick(click(ClickType.QUICK_CRAFT, 10, 1));   // add slot 10
        sync.onClick(click(ClickType.QUICK_CRAFT, 11, 1));   // add slot 11
        sync.onClick(click(ClickType.QUICK_CRAFT, -999, 2)); // end: 32 into each, cursor drained

        assertNull(sync.filter(new SetPlayerInventorySlotPacket(10, stone.withAmount(32))), "slot 10 got 32 - predicted");
        assertNull(sync.filter(new SetPlayerInventorySlotPacket(11, stone.withAmount(32))), "slot 11 got 32 - predicted");
        assertNull(sync.filter(new SetPlayerInventorySlotPacket(9, ItemStack.AIR)), "source slot emptied - predicted");
        assertNull(sync.filter(new SetCursorItemPacket(ItemStack.AIR)), "cursor drained - predicted");
    }

    @Test
    void redundantWindowItemsIsDroppedChangedIsSent() {
        InventorySync sync = new InventorySync();
        List<ItemStack> items = new ArrayList<>(Collections.nCopies(46, ItemStack.AIR));
        items.set(36, APPLES); // window 36 = hotbar 0
        assertNotNull(sync.filter(new WindowItemsPacket(0, 0, items, ItemStack.AIR)), "first full window sent + baselines");
        assertNull(sync.filter(new WindowItemsPacket(0, 0, items, ItemStack.AIR)), "identical resync is redundant - dropped");

        List<ItemStack> changed = new ArrayList<>(items);
        changed.set(36, APPLES.withAmount(15));
        assertNotNull(sync.filter(new WindowItemsPacket(0, 0, changed, ItemStack.AIR)), "a differing resync is sent");
    }

    @Test
    void shiftClickHotbarToMainMovesAndSuppresses() {
        InventorySync sync = new InventorySync();
        ItemStack stick = ItemStack.of(Material.STICK, 1);
        assertNotNull(sync.filter(new SetPlayerInventorySlotPacket(0, stick)), "anchor hotbar 0 = stick");
        sync.onClick(click(ClickType.QUICK_MOVE, 36, 0)); // shift-click hotbar 0 (window slot 36)
        assertNull(sync.filter(new SetPlayerInventorySlotPacket(9, stick)), "moved to first main slot 9 - predicted");
        assertNull(sync.filter(new SetPlayerInventorySlotPacket(0, ItemStack.AIR)), "hotbar 0 emptied - predicted");
    }

    @Test
    void shiftClickMergesThenOverflows() {
        InventorySync sync = new InventorySync();
        assertNotNull(sync.filter(new SetPlayerInventorySlotPacket(0, ItemStack.of(Material.STONE, 10))), "hotbar 0 = 10 stone");
        assertNotNull(sync.filter(new SetPlayerInventorySlotPacket(9, ItemStack.of(Material.STONE, 60))), "main 9 = 60 stone");
        sync.onClick(click(ClickType.QUICK_MOVE, 36, 0)); // shift-click the 10
        assertNull(sync.filter(new SetPlayerInventorySlotPacket(9, ItemStack.of(Material.STONE, 64))), "topped 60->64 - predicted");
        assertNull(sync.filter(new SetPlayerInventorySlotPacket(10, ItemStack.of(Material.STONE, 6))), "overflow 6 to next slot - predicted");
        assertNull(sync.filter(new SetPlayerInventorySlotPacket(0, ItemStack.AIR)), "source emptied - predicted");
    }

    @Test
    void doubleClickGathersMatchingStacks() {
        InventorySync sync = new InventorySync();
        assertNotNull(sync.filter(new SetPlayerInventorySlotPacket(10, ItemStack.of(Material.STONE, 20))), "slot 10 = 20 stone");
        assertNotNull(sync.filter(new SetPlayerInventorySlotPacket(11, ItemStack.of(Material.STONE, 30))), "slot 11 = 30 stone");
        sync.onClick(click(ClickType.PICKUP, 10, 0));     // pick up slot 10 -> cursor 20
        sync.onClick(click(ClickType.PICKUP_ALL, 10, 0)); // double-click: gather more to fill toward 64
        assertNull(sync.filter(new SetCursorItemPacket(ItemStack.of(Material.STONE, 50))), "cursor gathered 20+30=50 - predicted");
        assertNull(sync.filter(new SetPlayerInventorySlotPacket(11, ItemStack.AIR)), "slot 11 drained - predicted");
    }

    @Test
    void containerWindowClickLeavesMirrorUntouched() {
        InventorySync sync = new InventorySync();
        assertNotNull(sync.filter(new SetPlayerInventorySlotPacket(0, APPLES)), "anchor hotbar 0");
        sync.onClick(new ClientClickWindowPacket(3, 0, (short) 35, (byte) 0, ClickType.SWAP, Map.of(), ItemStack.Hash.of(ItemStack.AIR)));
        assertNotNull(sync.filter(new SetPlayerInventorySlotPacket(0, ItemStack.AIR)), "no window-0 prediction, so the echo is a real change");
    }
}
