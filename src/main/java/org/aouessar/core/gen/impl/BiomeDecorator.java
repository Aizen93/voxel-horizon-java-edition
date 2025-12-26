package org.aouessar.core.gen.impl;

import org.aouessar.core.gen.SurfaceDecorator;
import org.aouessar.core.world.Blocks;
import org.aouessar.core.world.layers.BiomeMap;
import org.aouessar.core.world.layers.Heightmap;
import org.aouessar.core.world.layers.LayerRect;
import org.aouessar.core.world.layers.SurfaceRules;
import org.aouessar.shared.EngineConfig;

public final class BiomeDecorator implements SurfaceDecorator {

    @Override
    public SurfaceRules generateSurfaceRules(Heightmap heightmap, BiomeMap biomeMap) {
        LayerRect rect = heightmap.rect();
        int n = rect.sizeX * rect.sizeZ;

        short[] top = new short[n];
        short[] filler = new short[n];
        byte[] depth = new byte[n];

        int i = 0;
        for (int z = 0; z < rect.sizeZ; z++) {
            int wz = rect.minZ + z;
            for (int x = 0; x < rect.sizeX; x++) {
                int wx = rect.minX + x;

                short biome = biomeMap.biomeIdAtUnchecked(wx, wz);
                int h = heightmap.heightAtUnchecked(wx, wz);

                short topBlock;
                if (h <= EngineConfig.SEA_LEVEL - 1) topBlock = Blocks.SAND;
                else if (biome == 1) topBlock = Blocks.SAND; // desert
                else if (biome == 2) topBlock = Blocks.SNOW; // cold
                else topBlock = Blocks.GRASS;

                top[i] = topBlock;
                filler[i] = Blocks.DIRT;
                depth[i] = 4;
                i++;
            }
        }

        return new SurfaceRules(rect, top, filler, depth);
    }
}