package org.aouessar.core.gen.impl;

import org.aouessar.core.gen.BiomeGenerator;
import org.aouessar.core.noise.FastNoiseLite;
import org.aouessar.core.world.layers.BiomeMap;
import org.aouessar.core.world.layers.Heightmap;
import org.aouessar.core.world.layers.LayerRect;
import org.aouessar.shared.EngineConfig;

public final class DefaultBiomeGenerator implements BiomeGenerator {

    // Biome IDs are yours to define later. For now: 0=plains,1=desert,2=snow
    @Override
    public BiomeMap generateBiomes(long seed, Heightmap heightmap) {
        LayerRect rect = heightmap.rect();
        int n = rect.sizeX * rect.sizeZ;
        short[] biomes = new short[n];

        FastNoiseLite fn = new FastNoiseLite((int) (seed ^ 0xD1B54A32D192ED03L));
        fn.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        fn.SetFrequency(0.001f);

        int i = 0;
        for (int z = 0; z < rect.sizeZ; z++) {
            int wz = rect.minZ + z;
            for (int x = 0; x < rect.sizeX; x++) {
                int wx = rect.minX + x;

                float temp = (fn.GetNoise(wx + 10000, wz - 10000) + 1f) * 0.5f; // 0..1
                int h = heightmap.heightAtUnchecked(wx, wz);

                short biome;
                if (h < EngineConfig.SEA_LEVEL - 8) biome = 0; // ocean-ish -> plains for now
                else if (temp < 0.33f) biome = 2;             // cold
                else if (temp > 0.70f) biome = 1;             // hot
                else biome = 0;                                // temperate

                biomes[i++] = biome;
            }
        }

        return new BiomeMap(rect, biomes);
    }
}