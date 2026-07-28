package io.github.term4.polyp.vri;

import io.github.term4.polyp.world.MechanicsWorld;
import net.minestom.server.coordinate.Point;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.PlayerBlockBreakEvent;
import net.minestom.server.event.player.PlayerStartDiggingEvent;
import net.minestom.server.instance.block.Block;
import net.minestom.server.network.packet.server.play.WorldEventPacket;
import org.jetbrains.annotations.NotNull;

/**
 * Punching fire puts it out - Minestom has neither vanilla path. 1.8: fire is not crosshair-targetable, so EVERY
 * dig-start douses the cell on the CLICKED FACE ({@code World.douseFire}, world event 1004); in CREATIVE a
 * successful douse consumes the click (the block survives). Modern targets the fire block itself and its instant
 * break fizzes ({@code BaseFireBlock.playerWillDestroy}, event 1009). The face douse is the {@code fireDouse}
 * toggle (a 1.8 mechanic, off in modern-flavored configs); the direct-break fizz is unconditional - both
 * generations play it and Minestom just drops it. Insta-breaks (creative, zero-tick blocks) never fire
 * StartDigging, so those ride the cancellable break event.
 */
final class FireDouse {

    /** Modern fire-extinguish world event; Via maps it onto 1.8's 1004. */
    private static final int FIZZ = 1009;

    private FireDouse() {}

    static void install(EventNode<@NotNull Event> node, Vri vri) {
        // slow digs: vanilla douses at dig START (survival then keeps digging the clicked block)
        node.addListener(PlayerStartDiggingEvent.class, e -> {
            if (e.isCancelled() || e.getPlayer().getGameMode() == GameMode.SPECTATOR) return;
            if (vri.configFor(e.getPlayer()).fireDouse) douse(e.getPlayer(), e.getBlockPosition().relative(e.getBlockFace()));
        });
        node.addListener(PlayerBlockBreakEvent.class, e -> {
            // insta-breaks never fire StartDigging; a creative douse CONSUMES the click (1.8: the block survives)
            if (vri.configFor(e.getPlayer()).fireDouse
                    && douse(e.getPlayer(), e.getBlockPosition().relative(e.getBlockFace()))
                    && e.getPlayer().getGameMode() == GameMode.CREATIVE) {
                e.setCancelled(true);
            }
            // the fire block broken directly (modern targeting): BaseFireBlock.playerWillDestroy's fizz
            if (isFire(e.getBlock())) {
                MechanicsWorld.of(e.getPlayer()).broadcast(new WorldEventPacket(FIZZ, e.getBlockPosition(), 0, false));
            }
            // orphaned fire above the broken block: vanilla's neighbor update removes it SILENTLY; Minestom runs none
            if (!e.isCancelled()) {
                MechanicsWorld world = MechanicsWorld.of(e.getPlayer());
                Point above = e.getBlockPosition().add(0, 1, 0);
                if (isFire(world.getBlock(above, Block.Getter.Condition.TYPE))) world.setBlock(above, Block.AIR);
            }
        });
    }

    private static boolean douse(Player miner, Point at) {
        MechanicsWorld world = MechanicsWorld.of(miner);
        if (!world.isChunkLoaded(at)) return false; // a horizontal face can cross into an unloaded neighbor
        if (!isFire(world.getBlock(at, Block.Getter.Condition.TYPE))) return false;
        world.setBlock(at, Block.AIR);
        world.broadcast(new WorldEventPacket(FIZZ, at, 0, false));
        return true;
    }

    private static boolean isFire(Block block) {
        return block.compare(Block.FIRE) || block.compare(Block.SOUL_FIRE);
    }
}
