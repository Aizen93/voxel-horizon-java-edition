package org.aouessar.core.gen.impl;

import org.aouessar.core.gen.WorldCarver;
import org.aouessar.core.noise.FastNoiseLite;
import org.aouessar.core.world.layers.CarveMask;
import org.aouessar.core.world.layers.Heightmap;
import org.aouessar.core.world.layers.LayerRect;

public final class DefaultWorldCarver implements WorldCarver {

    @Override
    public CarveMask generateCarveMask(long seed, Heightmap heightmap) {
        LayerRect rect = heightmap.rect();
        int n = rect.sizeX * rect.sizeZ;
        byte[] carved = new byte[n];

        // Placeholder "river-ish" mask based on noise threshold
        FastNoiseLite fn = new FastNoiseLite((int) (seed ^ 0x94D049BB133111EBL));
        fn.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        fn.SetFrequency(0.0025f);

        int i = 0;
        for (int z = 0; z < rect.sizeZ; z++) {
            int wz = rect.minZ + z;
            for (int x = 0; x < rect.sizeX; x++) {
                int wx = rect.minX + x;

                float v = fn.GetNoise(wx, wz);
                carved[i++] = (Math.abs(v) < 0.035f) ? (byte) 1 : (byte) 0;
            }
        }

        return new CarveMask(rect, carved);
    }
}