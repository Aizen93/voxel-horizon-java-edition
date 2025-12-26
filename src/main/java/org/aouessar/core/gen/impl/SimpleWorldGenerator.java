package org.aouessar.core.gen.impl;

import org.aouessar.core.gen.WorldGenerator;
import org.aouessar.core.noise.FastNoiseLite;
import org.aouessar.core.world.layers.Heightmap;
import org.aouessar.core.world.layers.LayerRect;
import org.aouessar.shared.EngineConfig;

public final class SimpleWorldGenerator implements WorldGenerator {

    @Override
    public Heightmap generateHeightmap(long seed, LayerRect rect) {
        int n = rect.sizeX * rect.sizeZ;
        int[] heights = new int[n];

        // Deterministic noise per region/seed
        FastNoiseLite fn = new FastNoiseLite((int) (seed ^ 0x9E3779B97F4A7C15L));
        fn.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        fn.SetFrequency(0.0015f);

        // Very basic starter: continents-ish + mountains-ish
        // (You will replace with your real terrain stack later.)
        int i = 0;
        for (int z = 0; z < rect.sizeZ; z++) {
            int wz = rect.minZ + z;
            for (int x = 0; x < rect.sizeX; x++) {
                int wx = rect.minX + x;

                float n0 = fn.GetNoise(wx, wz);           // [-1..1]
                float base = (n0 + 1f) * 0.5f;            // [0..1]

                int h = EngineConfig.SEA_LEVEL + Math.round((base - 0.5f) * 120f);
                // Clamp to Minecraft vertical bounds
                if (h < EngineConfig.MIN_Y) h = EngineConfig.MIN_Y;
                if (h > EngineConfig.MAX_Y) h = EngineConfig.MAX_Y;

                heights[i++] = h;
            }
        }

        return new Heightmap(rect, heights);
    }
}