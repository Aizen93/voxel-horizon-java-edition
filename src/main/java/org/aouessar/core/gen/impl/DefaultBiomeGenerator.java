package org.aouessar.core.gen.impl;

import org.aouessar.core.gen.BiomeGenerator;
import org.aouessar.core.math.GlobalTerrainUtils;
import org.aouessar.core.noise.FastNoiseLite;
import org.aouessar.core.world.layers.BiomeMap;
import org.aouessar.core.world.layers.Heightmap;
import org.aouessar.core.world.layers.LayerRect;
import org.aouessar.shared.EngineConfig;

/**
 * Biome generator with equal distribution across all biomes.
 * Uses a balanced climate grid system where each biome gets approximately equal area.
 * Temperature/humidity still influence placement for logical transitions.
 */
public final class DefaultBiomeGenerator implements BiomeGenerator {

    // Biome centers in (temperature, humidity) space - positioned for equal Voronoi areas
    // Each biome occupies roughly 1/7 of the climate space
    private static final float[][] BIOME_CENTERS = {
        // {temp, humid, biomeId}
        {0.15f, 0.50f, EngineConfig.BIOME_SNOW},      // Cold - any humidity
        {0.35f, 0.25f, EngineConfig.BIOME_PLAINS},    // Cool-temperate, dry
        {0.35f, 0.75f, EngineConfig.BIOME_FOREST},    // Cool-temperate, wet
        {0.55f, 0.50f, EngineConfig.BIOME_SWAMP},     // Mid-temperate, moderate
        {0.75f, 0.25f, EngineConfig.BIOME_DESERT},    // Hot, dry
        {0.75f, 0.55f, EngineConfig.BIOME_SAVANNA},   // Hot, moderate
        {0.85f, 0.85f, EngineConfig.BIOME_JUNGLE},    // Hot, very wet
    };

    @Override
    public BiomeMap generateBiomes(long seed, Heightmap heightmap) {
        LayerRect rect = heightmap.rect();
        int n = rect.sizeX * rect.sizeZ;
        short[] biomes = new short[n];

        // ---- Warp (keeps borders organic and wavy) ----
        FastNoiseLite warp = new FastNoiseLite(GlobalTerrainUtils.mixSeed(seed, 0x6A09E667));
        warp.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        warp.SetFrequency(EngineConfig.BIOME_WARP_FREQ);
        warp.SetFractalType(FastNoiseLite.FractalType.DomainWarpProgressive);
        warp.SetFractalOctaves(2);
        warp.SetFractalGain(0.5f);
        warp.SetFractalLacunarity(2.0f);
        warp.SetDomainWarpAmp(EngineConfig.BIOME_WARP_AMP_BLOCKS);

        // ---- Temperature noise (independent of terrain) ----
        FastNoiseLite tempN = new FastNoiseLite(GlobalTerrainUtils.mixSeed(seed, 0xBB67AE85));
        tempN.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        tempN.SetFrequency(EngineConfig.BIOME_TEMP_FREQ * 0.5f); // Larger scale for bigger zones
        tempN.SetFractalType(FastNoiseLite.FractalType.FBm);
        tempN.SetFractalOctaves(3);
        tempN.SetFractalGain(0.5f);
        tempN.SetFractalLacunarity(2.0f);

        // ---- Humidity noise (completely independent - fixes desert-coast correlation) ----
        FastNoiseLite humidN = new FastNoiseLite(GlobalTerrainUtils.mixSeed(seed, 0x3C6EF372));
        humidN.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        humidN.SetFrequency(EngineConfig.BIOME_HUMID_FREQ * 0.5f);
        humidN.SetFractalType(FastNoiseLite.FractalType.FBm);
        humidN.SetFractalOctaves(3);
        humidN.SetFractalGain(0.5f);
        humidN.SetFractalLacunarity(2.0f);

        // ---- Weirdness noise (for sub-biome variation) ----
        FastNoiseLite weirdN = new FastNoiseLite(GlobalTerrainUtils.mixSeed(seed, 0xA54FF53A));
        weirdN.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        weirdN.SetFrequency(EngineConfig.BIOME_WEIRD_FREQ);
        weirdN.SetFractalType(FastNoiseLite.FractalType.FBm);
        weirdN.SetFractalOctaves(3);
        weirdN.SetFractalGain(0.5f);
        weirdN.SetFractalLacunarity(2.0f);

        // ---- Voronoi jitter noise (makes boundaries less regular) ----
        FastNoiseLite jitterN = new FastNoiseLite(GlobalTerrainUtils.mixSeed(seed, 0xCAFEBABE));
        jitterN.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        jitterN.SetFrequency(1.0f / 256.0f);

        final int sea = EngineConfig.SEA_LEVEL;

        int i = 0;
        for (int z = 0; z < rect.sizeZ; z++) {
            int wz0 = rect.minZ + z;
            for (int x = 0; x < rect.sizeX; x++) {
                int wx0 = rect.minX + x;

                // Warp sampling coords for organic borders
                FastNoiseLite.Vector2 p = new FastNoiseLite.Vector2(wx0, wz0);
                warp.DomainWarp(p);
                float wx = p.x;
                float wz = p.y;

                // Different coordinate rotations to decorrelate temp and humidity
                float tx = 0.8660254f * wx + 0.5f * wz + 50000.0f;
                float tz = -0.5f * wx + 0.8660254f * wz - 50000.0f;

                float hx = -0.7071068f * wx + 0.7071068f * wz + 80000.0f;
                float hz = 0.7071068f * wx + 0.7071068f * wz - 30000.0f;

                // Sample climate values (0 to 1 range)
                float temp = GlobalTerrainUtils.to01(tempN.GetNoise(tx, tz));
                float humid = GlobalTerrainUtils.to01(humidN.GetNoise(hx, hz));
                float weird = GlobalTerrainUtils.to01(weirdN.GetNoise(wx + 30000.0f, wz + 30000.0f));

                int h = heightmap.heightAtUnchecked(wx0, wz0);

                // Ocean handling - use plains for deep ocean floor
                if (h < sea - 8) {
                    biomes[i++] = EngineConfig.BIOME_PLAINS;
                    continue;
                }

                // Altitude cooling (mountains get colder)
                float aboveSea = (h - sea) / 100.0f;
                if (aboveSea < 0f) aboveSea = 0f;
                if (aboveSea > 1f) aboveSea = 1f;
                temp = GlobalTerrainUtils.clamp01(temp - aboveSea * EngineConfig.BIOME_ALTITUDE_COOLING);

                // Add jitter to make Voronoi boundaries more natural
                float jitter = GlobalTerrainUtils.to01(jitterN.GetNoise(wx, wz)) * 0.12f - 0.06f;

                // Find closest biome using weighted Voronoi with smooth blending
                short biome = findClosestBiome(temp + jitter, humid + jitter, weird);

                biomes[i++] = biome;
            }
        }

        return new BiomeMap(rect, biomes);
    }

    /**
     * Find the closest biome center using Voronoi-like distance calculation.
     * Uses smooth blending near boundaries for natural transitions.
     */
    private short findClosestBiome(float temp, float humid, float weird) {
        temp = GlobalTerrainUtils.clamp01(temp);
        humid = GlobalTerrainUtils.clamp01(humid);

        float minDist = Float.MAX_VALUE;
        float secondMinDist = Float.MAX_VALUE;
        int closestIdx = 0;
        int secondClosestIdx = 0;

        // Find two closest biome centers
        for (int b = 0; b < BIOME_CENTERS.length; b++) {
            float dt = temp - BIOME_CENTERS[b][0];
            float dh = humid - BIOME_CENTERS[b][1];

            // Weighted distance - slightly favor temperature axis for more latitudinal feel
            float dist = dt * dt * 1.2f + dh * dh;

            if (dist < minDist) {
                secondMinDist = minDist;
                secondClosestIdx = closestIdx;
                minDist = dist;
                closestIdx = b;
            } else if (dist < secondMinDist) {
                secondMinDist = dist;
                secondClosestIdx = b;
            }
        }

        // Use weirdness to occasionally flip to second-closest biome at boundaries
        // This creates more natural, less geometric transitions
        float distDiff = secondMinDist - minDist;
        float blendZone = 0.04f; // How close distances need to be to blend

        if (distDiff < blendZone) {
            // We're near a boundary - use weird noise to determine which side
            float blendFactor = distDiff / blendZone;
            float threshold = 0.3f + blendFactor * 0.4f; // 0.3 to 0.7 based on distance

            if (weird < threshold) {
                return (short) BIOME_CENTERS[secondClosestIdx][2];
            }
        }

        return (short) BIOME_CENTERS[closestIdx][2];
    }
}
