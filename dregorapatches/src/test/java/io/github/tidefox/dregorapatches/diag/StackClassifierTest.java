package io.github.tidefox.dregorapatches.diag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class StackClassifierTest {

    private static StackTraceElement f(String cls, String method) {
        return new StackTraceElement(cls, method, null, -1);
    }

    @Test
    void syncLoadIsAttributedToFirstModFrame() {
        StackTraceElement[] st = {
                f("java.lang.Thread", "getStackTrace"),
                f("io.github.tidefox.dregorapatches.diag.ChunkLoadTracer", "onChunkLoad"),
                f("net.minecraft.world.chunk.Chunk", "onLoad"),
                f("net.minecraftforge.common.chunkio.ChunkIOProvider", "syncCallback"),
                f("net.minecraftforge.common.chunkio.ChunkIOExecutor", "syncChunkLoad"),
                f("net.minecraft.world.gen.ChunkProviderServer", "loadChunk"),
                f("net.minecraft.world.World", "getTileEntity"),
                f("nc.tile.dummy.TileDummy", "isMaster"),
                f("nc.tile.dummy.TileDummy", "getMaster"),
                f("net.minecraft.world.World", "updateEntities"),
        };
        assertEquals("nc.tile.dummy.TileDummy.isMaster", StackClassifier.classify(st));
    }

    @Test
    void playerAndAsyncLoadsAreSkipped() {
        assertNull(StackClassifier.classify(new StackTraceElement[] {
                f("net.minecraft.world.chunk.Chunk", "onLoad"),
                f("net.minecraft.server.management.PlayerChunkMapEntry", "providePlayerChunk"),
        }));
        assertNull(StackClassifier.classify(new StackTraceElement[] {
                f("net.minecraft.world.chunk.Chunk", "onLoad"),
                f("net.minecraftforge.common.chunkio.ChunkIOProvider", "syncCallback"),
                f("net.minecraftforge.common.chunkio.ChunkIOExecutor", "tick"),
        }));
    }

    @Test
    void vanillaOnlyStackNamesCallerAboveProvider() {
        StackTraceElement[] st = {
                f("net.minecraft.world.chunk.Chunk", "onLoad"),
                f("net.minecraftforge.common.chunkio.ChunkIOExecutor", "syncChunkLoad"),
                f("net.minecraft.world.gen.ChunkProviderServer", "loadChunk"),
                f("net.minecraft.world.gen.ChunkProviderServer", "provideChunk"),
                f("net.minecraft.world.World", "getChunk"),
                f("net.minecraft.block.BlockHopper", "updateTick"),
        };
        assertEquals("(vanilla) BlockHopper.updateTick", StackClassifier.classify(st));
    }
}
