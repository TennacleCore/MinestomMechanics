package io.github.term4.minestommechanics.platform.fixes.world;

import net.minestom.server.MinecraftServer;
import net.minestom.server.collision.CollisionUtils;
import net.minestom.server.component.DataComponents;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.PlayerHand;
import net.minestom.server.event.EventDispatcher;
import net.minestom.server.event.player.PlayerBlockInteractEvent;
import net.minestom.server.event.player.PlayerBlockPlaceEvent;
import net.minestom.server.event.player.PlayerUseItemOnBlockEvent;
import net.minestom.server.instance.Chunk;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockFace;
import net.minestom.server.instance.block.BlockHandler;
import net.minestom.server.instance.block.BlockManager;
import net.minestom.server.instance.block.rule.BlockPlacementRule;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.item.component.BlockPredicates;
import net.minestom.server.item.component.ItemBlockState;
import net.minestom.server.network.packet.client.play.ClientPlayerBlockPlacementPacket;
import net.minestom.server.network.packet.server.play.AcknowledgeBlockChangePacket;
import net.minestom.server.network.packet.server.play.BlockChangePacket;
import net.minestom.server.utils.chunk.ChunkUtils;
import net.minestom.server.world.DimensionType;

/**
 * Temporary local override of Minestom's {@code BlockPlacementListener}, applied via the packet-listener manager so
 * playtesting gets the fix without changing the pinned Minestom dependency. Identical to upstream except that a
 * cancelled placement is corrected with a targeted {@link BlockChangePacket} (+ ack) instead of a full chunk resend:
 * a chunk resend drops that chunk's entities from an older client's per-chunk entity index, so after placing a block
 * onto another player they become impossible to interact with until re-tracked. This is just temporary housing: the
 * fix has been PR'd upstream to Minestom (branch {@code fix/targeted-block-updates}); delete this class once that lands
 * on the pinned dependency and Minestom handles it natively. See {@link BlockPlacementFixConfig}.
 *
 * <p>{@link #listener} stays a faithful copy of upstream (only the {@code // FIX:} targeted-update lines differ). The
 * separate 1.8 self-placement compat fix ({@code fixes.client.SelfPlacementFix}) wraps this listener rather than
 * editing it, so no 1.8-specific logic lives here. Once the upstream fix lands, delete this class; that wrapper
 * repoints at the upstream listener.
 */
public final class BlockPlacementFix {
    private static final BlockManager BLOCK_MANAGER = MinecraftServer.getBlockManager();

    private BlockPlacementFix() {}

    /** Overrides Minestom's stock block-placement listener with the corrected one (server-wide, not reversible). */
    public static void install() {
        MinecraftServer.getPacketListenerManager().setPlayListener(ClientPlayerBlockPlacementPacket.class, BlockPlacementFix::listener);
    }

    public static void listener(ClientPlayerBlockPlacementPacket packet, Player player) {
        final PlayerHand hand = packet.hand();
        final BlockFace blockFace = packet.blockFace();
        Point blockPosition = packet.blockPosition();

        final Instance instance = player.getInstance();
        if (instance == null)
            return;

        final Chunk interactedChunk = instance.getChunkAt(blockPosition);
        if (!ChunkUtils.isLoaded(interactedChunk)) {
            return;
        }

        final ItemStack usedItem = player.getItemInHand(hand);
        final Block interactedBlock = instance.getBlock(blockPosition);

        final Point cursorPosition = new Vec(packet.cursorPositionX(), packet.cursorPositionY(), packet.cursorPositionZ());

        PlayerBlockInteractEvent playerBlockInteractEvent = new PlayerBlockInteractEvent(player, hand, instance, interactedBlock, blockPosition.asBlockVec(), cursorPosition, blockFace);
        EventDispatcher.call(playerBlockInteractEvent);
        boolean blockUse = playerBlockInteractEvent.isBlockingItemUse();
        if (!playerBlockInteractEvent.isCancelled()) {
            final var handler = interactedBlock.handler();
            if (handler != null) {
                blockUse |= !handler.onInteract(new BlockHandler.Interaction(interactedBlock, instance, blockFace, blockPosition, cursorPosition, player, hand));
            }
        }
        if (blockUse) {
            player.sendPacket(new AcknowledgeBlockChangePacket(packet.sequence()));
            return;
        }

        final Material useMaterial = usedItem.material();
        if (!useMaterial.isBlock()) {
            PlayerUseItemOnBlockEvent event = new PlayerUseItemOnBlockEvent(player, hand, usedItem, blockPosition, cursorPosition, blockFace);
            EventDispatcher.call(event);
            player.sendPacket(new AcknowledgeBlockChangePacket(packet.sequence()));
            return;
        }

        boolean canPlaceBlock = true;
        if (player.getGameMode() == GameMode.SPECTATOR) {
            canPlaceBlock = false;
        } else if (player.getGameMode() == GameMode.ADVENTURE) {
            BlockPredicates placePredicate = usedItem.get(DataComponents.CAN_PLACE_ON, BlockPredicates.NEVER);
            canPlaceBlock = placePredicate.test(interactedBlock);
        }

        Point placementPosition = blockPosition;
        var interactedPlacementRule = BLOCK_MANAGER.getBlockPlacementRule(interactedBlock);
        if (!interactedBlock.isAir() && (interactedPlacementRule == null || !interactedPlacementRule.isSelfReplaceable(
                new BlockPlacementRule.Replacement(interactedBlock, blockFace, cursorPosition, false, useMaterial)))) {
            placementPosition = blockPosition.relative(blockFace);

            var placementBlock = instance.getBlock(placementPosition);
            var placementRule = BLOCK_MANAGER.getBlockPlacementRule(placementBlock);
            if (!placementBlock.registry().isReplaceable() && !(placementRule != null && placementRule.isSelfReplaceable(
                    new BlockPlacementRule.Replacement(placementBlock, blockFace, cursorPosition, true, useMaterial)))) {
                canPlaceBlock = false;
            }
        }

        final DimensionType instanceDim = instance.getCachedDimensionType();
        if (placementPosition.y() >= instanceDim.maxY() || placementPosition.y() < instanceDim.minY()) {
            return;
        }

        if (!instance.getWorldBorder().inBounds(placementPosition)) {
            canPlaceBlock = false;
        }

        if (!canPlaceBlock) {
            final Block block = instance.getBlock(placementPosition);
            player.sendPacket(new BlockChangePacket(placementPosition, block));
            player.sendPacket(new AcknowledgeBlockChangePacket(packet.sequence())); // FIX: ack the cancelled placement
            return;
        }

        final Chunk chunk = instance.getChunkAt(placementPosition);
        if (chunk == null || !ChunkUtils.isLoaded(chunk)) {
            return;
        }
        if (chunk.isReadOnly()) {
            rollback(player, instance, placementPosition, packet.sequence()); // FIX: targeted rollback, not a chunk resend
            return;
        }

        final ItemBlockState blockState = usedItem.get(DataComponents.BLOCK_STATE, ItemBlockState.EMPTY);
        final Block placedBlock = blockState.apply(useMaterial.block());

        Entity collisionEntity = CollisionUtils.canPlaceBlockAt(instance, placementPosition, placedBlock);
        if (collisionEntity != null) {
            // FIX: send a targeted block change (not a chunk resend) so the colliding entity stays in the client's
            // per-chunk index and remains interactable. Ack regardless so the client's prediction sequence resolves.
            if (collisionEntity != player) {
                player.getInventory().update();
                player.sendPacket(new BlockChangePacket(placementPosition, instance.getBlock(placementPosition)));
            }
            player.sendPacket(new AcknowledgeBlockChangePacket(packet.sequence()));
            return;
        }

        PlayerBlockPlaceEvent playerBlockPlaceEvent = new PlayerBlockPlaceEvent(player, instance, placedBlock, blockFace, placementPosition.asBlockVec(), cursorPosition, packet.hand());
        playerBlockPlaceEvent.consumeBlock(player.getGameMode() != GameMode.CREATIVE);
        playerBlockPlaceEvent.setDoBlockUpdates(blockState.equals(useMaterial.prototype().get(DataComponents.BLOCK_STATE, ItemBlockState.EMPTY)));
        EventDispatcher.call(playerBlockPlaceEvent);
        if (playerBlockPlaceEvent.isCancelled()) {
            rollback(player, instance, placementPosition, packet.sequence()); // FIX: targeted rollback, not a chunk resend
            return;
        }

        Block resultBlock = playerBlockPlaceEvent.getBlock();
        instance.placeBlock(new BlockHandler.PlayerPlacement(resultBlock, instance.getBlock(placementPosition), instance, placementPosition, player, hand, blockFace,
                packet.cursorPositionX(), packet.cursorPositionY(), packet.cursorPositionZ()), playerBlockPlaceEvent.shouldDoBlockUpdates());
        player.sendPacket(new AcknowledgeBlockChangePacket(packet.sequence()));
        if (playerBlockPlaceEvent.doesConsumeBlock()) {
            final ItemStack newUsedItem = usedItem.consume(1);
            player.setItemInHand(hand, newUsedItem);
        } else {
            player.getInventory().update();
        }
    }

    /** FIX: corrects a cancelled placement with a targeted block change (+ ack) instead of resending the whole chunk. */
    private static void rollback(Player player, Instance instance, Point placementPosition, int sequence) {
        player.getInventory().update();
        player.sendPacket(new BlockChangePacket(placementPosition, instance.getBlock(placementPosition)));
        player.sendPacket(new AcknowledgeBlockChangePacket(sequence));
    }
}
