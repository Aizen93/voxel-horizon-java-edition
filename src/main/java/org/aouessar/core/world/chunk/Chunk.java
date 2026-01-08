package org.aouessar.core.world.chunk;

import org.aouessar.core.math.Height;
import org.aouessar.shared.EngineConfig;

public final class Chunk {

    private final int cx;
    private final int cz;
    private final short[] blocks; // [x,z,y] flattened

    public Chunk(int cx, int cz, short[] blocks) {
        this.cx = cx;
        this.cz = cz;
        int expected = EngineConfig.CHUNK_SIZE * EngineConfig.CHUNK_SIZE * EngineConfig.WORLD_HEIGHT;
        if (blocks.length != expected) throw new IllegalArgumentException("Chunk buffer size mismatch");
        this.blocks = blocks;
    }

    public int cx() {
        return cx;
    }

    public int cz() {
        return cz;
    }

    public short getBlock(int localX, int worldY, int localZ) {
        int ly = Height.toLocalY(worldY);
        if (localX < 0 || localX >= EngineConfig.CHUNK_SIZE) return 0;
        if (localZ < 0 || localZ >= EngineConfig.CHUNK_SIZE) return 0;
        if (!Height.isValidLocalY(ly)) return 0;
        return blocks[index(localX, ly, localZ)];
    }

    public void setBlock(int localX, int worldY, int localZ, short id) {
        int ly = Height.toLocalY(worldY);
        if (localX < 0 || localX >= EngineConfig.CHUNK_SIZE) return;
        if (localZ < 0 || localZ >= EngineConfig.CHUNK_SIZE) return;
        if (!Height.isValidLocalY(ly)) return;
        blocks[index(localX, ly, localZ)] = id;
    }

    public short[] raw() {
        return blocks;
    }

    public static Chunk emptyChunk(int cx, int cz) {
        short[] b = new short[EngineConfig.CHUNK_SIZE * EngineConfig.CHUNK_SIZE * EngineConfig.WORLD_HEIGHT];
        // default 0 = AIR
        return new Chunk(cx, cz, b);
    }

    private int index(int x, int ly, int z) {
        // Layout: ((z * CHUNK_SIZE) + x) * WORLD_HEIGHT + ly
        return ((z * EngineConfig.CHUNK_SIZE) + x) * EngineConfig.WORLD_HEIGHT + ly;
    }
}