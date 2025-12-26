package org.aouessar.core.api;

import org.aouessar.core.world.chunk.Chunk;

public interface ChunkProvider {
    Chunk getChunk(int cx, int cz);
}