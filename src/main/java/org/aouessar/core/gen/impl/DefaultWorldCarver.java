package org.aouessar.core.gen.impl;

import org.aouessar.core.gen.WorldCarver;
import org.aouessar.core.world.layers.CarveMask;
import org.aouessar.core.world.layers.Heightmap;
import org.aouessar.core.world.layers.LayerRect;
import org.aouessar.shared.EngineConfig;

/**
 * Marks river channel columns.
 * <p>
 * The heightmap already contains the river valleys (carved by
 * {@link TerrainColumnSampler}); this layer just records which columns belong
 * to the water channel so ChunkBuilder can lay a riverbed and skip tree
 * placement there.
 */
public final class DefaultWorldCarver implements WorldCarver {

    private record Holder(long seed, RiverColumnSampler sampler) {}
    private volatile Holder holder;

    @Override
    public CarveMask generateCarveMask(long seed, Heightmap heightmap) {
        LayerRect rect = heightmap.rect();
        int n = rect.sizeX * rect.sizeZ;
        byte[] carved = new byte[n];

        RiverColumnSampler rivers = samplerFor(seed);
        final int sea = EngineConfig.SEA_LEVEL;

        int i = 0;
        for (int z = 0; z < rect.sizeZ; z++) {
            int wz = rect.minZ + z;
            for (int x = 0; x < rect.sizeX; x++) {
                int wx = rect.minX + x;

                // Only columns whose (already valley-shaped) surface sits near
                // or below sea level are actual river channel/bank columns.
                int surfaceY = heightmap.heightAtUnchecked(wx, wz);
                if (surfaceY > sea + 3) {
                    carved[i++] = 0;
                    continue;
                }

                float channel = RiverColumnSampler.channel01(rivers.valley01(wx, wz));
                carved[i++] = (byte) Math.round(channel * 255f);
            }
        }

        return new CarveMask(rect, carved);
    }

    private RiverColumnSampler samplerFor(long seed) {
        Holder h = holder;
        if (h == null || h.seed != seed) {
            h = new Holder(seed, new RiverColumnSampler(seed));
            holder = h;
        }
        return h.sampler();
    }
}
