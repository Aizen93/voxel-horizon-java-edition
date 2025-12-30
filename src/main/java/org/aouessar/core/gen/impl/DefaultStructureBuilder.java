package org.aouessar.core.gen.impl;

import org.aouessar.core.gen.StructureBuilder;
import org.aouessar.core.world.Blocks;
import org.aouessar.core.world.layers.BiomeMap;
import org.aouessar.core.world.layers.Heightmap;
import org.aouessar.core.world.layers.LayerRect;
import org.aouessar.core.world.layers.StructureMap;

import java.util.ArrayList;
import java.util.List;

public final class DefaultStructureBuilder implements StructureBuilder {

    private static final int CHUNK_SIZE = 16;
    private static final int TRIES_PER_CHUNK = 6;
    private static final double CHANCE = 0.5;

    @Override
    public StructureMap placeStructures(long seed, Heightmap heightmap, BiomeMap biomeMap) {
        LayerRect rect = heightmap.rect();
        List<StructureMap.Placement> placements = new ArrayList<>();

        int minChunkX = Math.floorDiv(rect.minX, CHUNK_SIZE);
        int minChunkZ = Math.floorDiv(rect.minZ, CHUNK_SIZE);
        int maxChunkX = Math.floorDiv(rect.maxXExclusive() - 1, CHUNK_SIZE);
        int maxChunkZ = Math.floorDiv(rect.maxZExclusive() - 1, CHUNK_SIZE);

        for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
            for (int cx = minChunkX; cx <= maxChunkX; cx++) {

                for (int i = 0; i < TRIES_PER_CHUNK; i++) {
                    long h = hash(seed, cx, cz, i);

                    int wx = cx * CHUNK_SIZE + (int) (h & 15);
                    int wz = cz * CHUNK_SIZE + (int) ((h >>> 4) & 15);

                    if (!rect.contains(wx, wz)) continue;

                    double r = ((h >>> 8) & 0xFFFF) / 65535.0;
                    if (r > CHANCE) continue;

                    int surfaceY = heightmap.heightAt(wx, wz);

                    placements.add(
                        new StructureMap.Placement(
                            wx,
                            surfaceY + 1,
                            wz,
                            Blocks.BUSH
                        )
                    );
                }
            }
        }

        return new StructureMap(rect, placements);
    }

    private static long hash(long seed, int cx, int cz, int i) {
        long v = seed;
        v ^= cx * 0x632BE59BD9B4E019L;
        v ^= cz * 0x9E3779B97F4A7C15L;
        v ^= i * 0x85157AF5L;
        v ^= (v >>> 27);
        v *= 0x94D049BB133111EBL;
        return v ^ (v >>> 31);
    }
}