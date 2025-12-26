package org.aouessar.core.world.chunk;

import org.aouessar.core.world.Blocks;
import org.aouessar.core.world.Region;
import org.aouessar.core.world.WorldGrid;
import org.aouessar.core.world.layers.RegionLayers;
import org.aouessar.shared.EngineConfig;

public final class ChunkBuilder {

    public Chunk buildChunk(Region region, int cx, int cz) {
        // Assumes region owns this chunk; caller ensures mapping correctness.
        short[] blocks = new short[EngineConfig.CHUNK_SIZE * EngineConfig.CHUNK_SIZE * EngineConfig.WORLD_HEIGHT];
        Chunk chunk = new Chunk(cx, cz, blocks);

        RegionLayers layers = region.layers();

        int chunkOriginX = WorldGrid.chunkOriginBlockX(cx);
        int chunkOriginZ = WorldGrid.chunkOriginBlockZ(cz);

        for (int lz = 0; lz < EngineConfig.CHUNK_SIZE; lz++) {
            int wz = chunkOriginZ + lz;
            for (int lx = 0; lx < EngineConfig.CHUNK_SIZE; lx++) {
                int wx = chunkOriginX + lx;

                int surfaceY = layers.heightmap().heightAt(wx, wz);
                short top = layers.surfaceRules().topBlockAt(wx, wz);
                short filler = layers.surfaceRules().fillerBlockAt(wx, wz);
                int depth = layers.surfaceRules().fillerDepthAt(wx, wz);

                boolean carved = layers.carveMask().isCarvedColumn(wx, wz);

                // Fill from MIN_Y to surface
                for (int wy = EngineConfig.MIN_Y; wy <= EngineConfig.MAX_Y; wy++) {
                    short id;

                    if (wy > surfaceY) {
                        id = (wy <= EngineConfig.SEA_LEVEL) ? Blocks.WATER : Blocks.AIR;
                    } else {
                        // solid
                        if (carved && wy >= surfaceY - 2 && wy <= surfaceY) {
                            id = Blocks.AIR; // placeholder river cut
                        } else if (wy == surfaceY) {
                            id = top;
                        } else if (wy >= surfaceY - depth) {
                            id = filler;
                        } else {
                            id = Blocks.STONE;
                        }
                    }

                    chunk.setBlock(lx, wy, lz, id);
                }
            }
        }

        // Structures: (future) apply placements deterministically here
        return chunk;
    }
}