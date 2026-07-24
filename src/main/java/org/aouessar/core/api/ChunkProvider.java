package org.aouessar.core.api;

import org.aouessar.core.world.chunk.Chunk;

public interface ChunkProvider {
    Chunk getChunk(int cx, int cz);

    /**
     * Player edit: set one block (world-space coords, world Y). The edit must
     * survive chunk eviction/rebuild. Returns false if unsupported.
     */
    default boolean setBlock(int wx, int wy, int wz, short id) {
        return false;
    }
}