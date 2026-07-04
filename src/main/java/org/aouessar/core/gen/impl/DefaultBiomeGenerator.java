package org.aouessar.core.gen.impl;

import org.aouessar.core.gen.BiomeGenerator;
import org.aouessar.core.world.layers.BiomeMap;
import org.aouessar.core.world.layers.Heightmap;
import org.aouessar.core.world.layers.LayerRect;
import org.aouessar.shared.EngineConfig;

/**
 * Two-Category Biome Generator - separates biomes into geographic (water) and climate (land) types.
 *
 * Geographic biomes (determined by altitude - below sea level):
 * - Ocean: below sea level
 * - Deep Ocean: significantly below sea level
 *
 * Climate biomes (determined by temperature/humidity grid, applied to land only):
 * - Snow → Plains → Forest → Jungle (cold-to-hot, wet)
 * - Snow → Plains → Savanna → Desert (cold-to-hot, dry)
 *
 * Swamp is a hybrid biome: requires low elevation, high humidity, AND must be inland
 * (not near ocean coastlines). Swamps form in river valleys and low-lying inland areas.
 *
 * The actual per-column classification lives in {@link ClimateColumnSampler} and is
 * shared with the far-field LOD sampler; this class adds the region-level heightmap
 * inland test for swamps and fills the layer array.
 */
public final class DefaultBiomeGenerator implements BiomeGenerator {

    // Per-seed sampler cache (see SimpleWorldGenerator for the same pattern).
    private record Holder(long seed, ClimateColumnSampler sampler) {}
    private volatile Holder holder;

    @Override
    public BiomeMap generateBiomes(long seed, Heightmap heightmap) {
        LayerRect rect = heightmap.rect();
        int n = rect.sizeX * rect.sizeZ;
        short[] biomes = new short[n];

        ClimateColumnSampler sampler = samplerFor(seed);
        final int sea = EngineConfig.SEA_LEVEL;

        ClimateColumnSampler.InlandTest inland =
                (wx, wz) -> computeDistanceFromOcean(heightmap, rect, wx, wz, sea)
                        >= EngineConfig.SWAMP_MIN_DISTANCE_FROM_OCEAN;

        int i = 0;
        for (int z = 0; z < rect.sizeZ; z++) {
            int wz = rect.minZ + z;
            for (int x = 0; x < rect.sizeX; x++) {
                int wx = rect.minX + x;
                int h = heightmap.heightAtUnchecked(wx, wz);
                biomes[i++] = sampler.biomeAt(wx, wz, h, inland);
            }
        }

        return new BiomeMap(rect, biomes);
    }

    private ClimateColumnSampler samplerFor(long seed) {
        Holder h = holder;
        if (h == null || h.seed != seed) {
            h = new Holder(seed, new ClimateColumnSampler(seed));
            holder = h;
        }
        return h.sampler();
    }

    /**
     * Compute the minimum distance from this position to ocean (below sea level).
     * Used to ensure swamps are inland, not near coastlines.
     */
    private int computeDistanceFromOcean(Heightmap heightmap, LayerRect rect, int wx, int wz, int sea) {
        // Check in expanding rings until we find ocean or reach max distance.
        // maxDist must not exceed REGION_LAYER_PAD_BLOCKS: sampling further than the
        // padding means base-rect edge columns silently skip out-of-rect samples and
        // the same column can classify differently in two neighboring regions (seam).
        final int maxDist = EngineConfig.REGION_LAYER_PAD_BLOCKS; // 16

        for (int dist = 4; dist <= maxDist; dist += 4) {
            // Sample points at this distance
            for (int d = -dist; d <= dist; d += 4) {
                // Check all 4 sides of the square at this distance
                int[][] checks = {
                    {wx + d, wz - dist}, {wx + d, wz + dist},
                    {wx - dist, wz + d}, {wx + dist, wz + d}
                };

                for (int[] check : checks) {
                    int sx = check[0];
                    int sz = check[1];
                    if (sx >= rect.minX && sx < rect.maxXExclusive() && sz >= rect.minZ && sz < rect.maxZExclusive()) {
                        int sampleH = heightmap.heightAtUnchecked(sx, sz);
                        if (sampleH < sea) {
                            return dist;
                        }
                    }
                }
            }
        }

        return maxDist + 1; // No ocean found nearby
    }
}
