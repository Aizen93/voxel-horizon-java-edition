package org.aouessar.core.gen.impl;

import org.aouessar.core.gen.WorldGenerator;
import org.aouessar.core.world.layers.Heightmap;
import org.aouessar.core.world.layers.LayerRect;

/**
 * Full-resolution heightmap generation for the region pipeline.
 * All terrain math lives in {@link TerrainColumnSampler} so the far-field
 * LOD sampler produces byte-identical heights.
 */
public final class SimpleWorldGenerator implements WorldGenerator {

    // Cache the sampler per seed: noise setup is not free and generateHeightmap
    // is called once per region on worker threads. Single volatile holder so
    // concurrent readers always see a consistent (seed, sampler) pair.
    private record Holder(long seed, TerrainColumnSampler sampler) {}
    private volatile Holder holder;

    @Override
    public Heightmap generateHeightmap(long seed, LayerRect rect) {
        TerrainColumnSampler sampler = samplerFor(seed);

        int n = rect.sizeX * rect.sizeZ;
        int[] heights = new int[n];

        int i = 0;
        for (int z = 0; z < rect.sizeZ; z++) {
            int wz = rect.minZ + z;
            for (int x = 0; x < rect.sizeX; x++) {
                int wx = rect.minX + x;
                heights[i++] = sampler.heightAt(wx, wz);
            }
        }

        return new Heightmap(rect, heights);
    }

    private TerrainColumnSampler samplerFor(long seed) {
        Holder h = holder;
        if (h == null || h.seed != seed) {
            h = new Holder(seed, new TerrainColumnSampler(seed));
            holder = h;
        }
        return h.sampler();
    }
}
