package io.github.term4.polyp.vri;

import io.github.term4.polyp.testsupport.FakePlayer;
import io.github.term4.polyp.testsupport.HeadlessServerTest;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.event.EventDispatcher;
import net.minestom.server.event.player.PlayerBlockBreakEvent;
import net.minestom.server.event.player.PlayerStartDiggingEvent;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockFace;
import net.minestom.server.network.packet.server.play.WorldEventPacket;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 1.8 face douse (dig-start extinguishes fire on the clicked face, World.douseFire) + the direct-break fizz. */
class FireDouseTest extends HeadlessServerTest {

    private static final int FIZZ = 1009;
    private static FakePlayer miner;

    @BeforeAll
    static void install() {
        Vri.install(polyp, VriConfig.builder().fireDouse(true).build());
        miner = FakePlayer.connect(instance, new Pos(20.5, 43, 20.5), "FireMiner");
    }

    private static long fizzes() {
        return miner.sent(WorldEventPacket.class).stream().filter(p -> p.effectId() == FIZZ).count();
    }

    @Test
    void digStartDousesTheFireOnTheClickedFace() {
        BlockVec base = new BlockVec(20, 45, 22);
        instance.setBlock(base, Block.STONE);
        instance.setBlock(base.add(0, 1, 0), Block.FIRE);
        long before = fizzes();
        EventDispatcher.call(new PlayerStartDiggingEvent(miner.player, instance, Block.STONE, base, BlockFace.TOP));
        assertTrue(instance.getBlock(base.add(0, 1, 0)).isAir(), "the fire on the clicked face is doused");
        assertEquals(Block.STONE, instance.getBlock(base), "the clicked block itself is untouched");
        assertEquals(before + 1, fizzes(), "one extinguish fizz");
    }

    @Test
    void digStartWithoutAdjacentFireDoesNothing() {
        BlockVec base = new BlockVec(24, 45, 22);
        instance.setBlock(base, Block.STONE);
        long before = fizzes();
        EventDispatcher.call(new PlayerStartDiggingEvent(miner.player, instance, Block.STONE, base, BlockFace.TOP));
        assertEquals(before, fizzes());
    }

    @Test
    void directFireBreakFizzes() {
        BlockVec pos = new BlockVec(28, 45, 22);
        instance.setBlock(pos, Block.FIRE);
        long before = fizzes();
        EventDispatcher.call(new PlayerBlockBreakEvent(miner.player, instance, Block.FIRE, Block.AIR, pos, BlockFace.TOP));
        assertEquals(before + 1, fizzes(), "the modern instabreak path fizzes (BaseFireBlock.playerWillDestroy)");
        instance.setBlock(pos, Block.AIR);
    }

    /** Breaking the SUPPORT from a side face orphans the fire above: removed silently (vanilla's neighbor update). */
    @Test
    void breakingTheSupportRemovesTheOrphanedFireSilently() {
        BlockVec base = new BlockVec(36, 45, 22);
        instance.setBlock(base, Block.STONE);
        instance.setBlock(base.add(0, 1, 0), Block.FIRE);
        long before = fizzes();
        instance.setBlock(base, Block.AIR); // the break event's world state: support already gone
        EventDispatcher.call(new PlayerBlockBreakEvent(miner.player, instance, Block.STONE, Block.AIR, base, BlockFace.NORTH));
        assertTrue(instance.getBlock(base.add(0, 1, 0)).isAir(), "the floating fire is removed");
        assertEquals(before, fizzes(), "support-loss removal is silent");
    }

    /** Creative insta-breaks skip StartDigging entirely; the douse rides the break event and CONSUMES the click. */
    @Test
    void creativeDouseConsumesTheClickAndKeepsTheBlock() {
        miner.player.setGameMode(net.minestom.server.entity.GameMode.CREATIVE);
        try {
            BlockVec base = new BlockVec(32, 45, 22);
            instance.setBlock(base, Block.STONE);
            instance.setBlock(base.add(0, 1, 0), Block.FIRE);
            var breakEvent = new PlayerBlockBreakEvent(miner.player, instance, Block.STONE, Block.AIR, base, BlockFace.TOP);
            EventDispatcher.call(breakEvent);
            assertTrue(breakEvent.isCancelled(), "the douse consumes the creative click - the block survives");
            assertTrue(instance.getBlock(base.add(0, 1, 0)).isAir(), "the fire is doused");
        } finally {
            miner.player.setGameMode(net.minestom.server.entity.GameMode.SURVIVAL);
        }
    }
}
