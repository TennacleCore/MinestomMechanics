package io.github.term4.minestommechanics.platform.fixes.world;

import net.minestom.server.MinecraftServer;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.LightingChunk;
import net.minestom.server.instance.Section;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.palette.Palette;
import net.minestom.server.network.ConnectionState;
import net.minestom.server.network.NetworkBuffer;
import net.minestom.server.network.packet.server.CachedPacket;
import net.minestom.server.network.packet.server.SendablePacket;
import net.minestom.server.network.packet.server.play.ChunkDataPacket;
import net.minestom.server.network.packet.server.play.data.ChunkData;
import org.jetbrains.annotations.NotNull;

/**
 * Temporary Minestom workaround: stock {@code DynamicChunk} fakes the 26.1 chunk-section fluidCount as
 * {@code blockCount > 0 ? 1 : 0}. The client reads it off the wire (no recompute), tracks it incrementally per block update,
 * and gates a whole section's fluid on {@code fluidCount > 0} - so one update zeroes the faked count and modern clients walk
 * straight through the remaining water. This re-serializes each section with the real count (liquids + waterlogged), reusing
 * the base packet's heightmaps + light. Install with {@code instance.setChunkSupplier(FluidCountChunk::new)}; remove once
 * Minestom ships the proper count.
 */
public class FluidCountChunk extends LightingChunk {

    private final CachedPacket fixedCache = new CachedPacket(this::createFixedChunkPacket);

    public FluidCountChunk(@NotNull Instance instance, int chunkX, int chunkZ) {
        super(instance, chunkX, chunkZ);
    }

    @Override
    public SendablePacket getFullDataPacket() {
        return fixedCache;
    }

    @Override
    public void invalidate() {
        super.invalidate();
        fixedCache.invalidate();
    }

    private @NotNull ChunkDataPacket createFixedChunkPacket() {
        // Reuse the stock packet's heightmaps/light/block-entities (correct); only its per-section fluidCount is faked.
        if (!(SendablePacket.extractServerPacket(ConnectionState.PLAY, super.getFullDataPacket()) instanceof ChunkDataPacket base)) {
            throw new IllegalStateException("DynamicChunk did not yield a ChunkDataPacket");
        }
        final NetworkBuffer.Type<ChunkData.Section> sectionSerializer =
                ChunkData.Section.networkType(MinecraftServer.getBiomeRegistry().size());
        final byte[] data;
        lockReadLock();
        try {
            data = NetworkBuffer.makeArray(buffer -> {
                for (Section section : getSections()) {
                    final Palette blockPalette = section.blockPalette();
                    buffer.write(sectionSerializer, new ChunkData.Section(
                            (short) blockPalette.count(), countFluids(blockPalette), blockPalette, section.biomePalette()));
                }
            });
        } finally {
            unlockReadLock();
        }
        final ChunkData baseData = base.chunkData();
        return new ChunkDataPacket(getChunkX(), getChunkZ(),
                new ChunkData(baseData.heightmaps(), data, baseData.blockEntities()), base.lightData());
    }

    /** Vanilla {@code LevelChunkSection.fluidCount}: blocks with a non-empty fluid (liquids + waterlogged); uniform sections stay O(1). */
    private static short countFluids(@NotNull Palette blockPalette) {
        final int single = blockPalette.singleValue();
        if (single != -1) return isFluid(single) ? (short) blockPalette.count() : 0;
        final int[] count = {0};
        blockPalette.getAllPresent((x, y, z, stateId) -> {
            if (isFluid(stateId)) count[0]++;
        });
        return (short) count[0];
    }

    private static boolean isFluid(int blockStateId) {
        final Block block = Block.fromStateId(blockStateId);
        return block != null && (block.isLiquid() || "true".equals(block.getProperty("waterlogged")));
    }
}
