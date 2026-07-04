package org.aouessar.renderer.mesh;

import org.aouessar.core.api.ChunkProvider;
import org.aouessar.core.world.Blocks;
import org.aouessar.core.world.chunk.Chunk;
import org.aouessar.shared.EngineConfig;

public final class BlockAccessor {
    private final ChunkProvider provider;
    private final int cs = EngineConfig.CHUNK_SIZE;
    private final int h  = EngineConfig.WORLD_HEIGHT;

    BlockAccessor(ChunkProvider provider) {
        this.provider = provider;
    }

    short blockAtWorld(int wx, int wy, int wz) {
        if (wy < 0 || wy >= h) return Blocks.AIR;

        int cx = Math.floorDiv(wx, cs);
        int cz = Math.floorDiv(wz, cs);
        int lx = Math.floorMod(wx, cs);
        int lz = Math.floorMod(wz, cs);

        Chunk c = provider.getChunk(cx, cz);
        if (c == null) return Blocks.AIR;
        short[] raw = c.raw();
        return raw[index(cs, h, lx, wy, lz)];
    }

    public static int index(int cs, int h, int x, int y, int z) {
        return ((z * cs) + x) * h + y;
    }
}