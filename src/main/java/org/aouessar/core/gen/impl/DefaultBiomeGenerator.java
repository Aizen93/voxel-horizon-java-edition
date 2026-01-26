package org.aouessar.core.gen.impl;

import org.aouessar.core.gen.BiomeGenerator;
import org.aouessar.core.math.GlobalTerrainUtils;
import org.aouessar.core.noise.FastNoiseLite;
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
 * NOTE: Terrain features like mountains, beaches, cliffs, and lakes are NOT biomes.
 * They are handled by:
 * - WorldGenerator (heightmap): mountains, hills, valleys
 * - ChunkBuilder: beach surface materials based on proximity to water
 * - WaterGenerator: lakes and rivers
 *
 * Rules:
 * - Ocean is a real biome, determined by altitude (below sea level)
 * - Opposite climate biomes (e.g., Snow and Desert) never directly neighbor
 * - Biomes form large, continuous regions controlled via BIOME_SIZE_SCALE
 */
public final class DefaultBiomeGenerator implements BiomeGenerator {

    @Override
    public BiomeMap generateBiomes(long seed, Heightmap heightmap) {
        LayerRect rect = heightmap.rect();
        int n = rect.sizeX * rect.sizeZ;
        short[] biomes = new short[n];

        // ---- Domain Warp for organic, wavy borders ----
        FastNoiseLite warp = new FastNoiseLite(GlobalTerrainUtils.mixSeed(seed, 0x6A09E667));
        warp.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        warp.SetFrequency(EngineConfig.BIOME_WARP_FREQ);
        warp.SetFractalType(FastNoiseLite.FractalType.DomainWarpProgressive);
        warp.SetFractalOctaves(3);
        warp.SetFractalGain(0.5f);
        warp.SetFractalLacunarity(2.0f);
        warp.SetDomainWarpAmp(EngineConfig.BIOME_WARP_AMP_BLOCKS);

        // ---- Temperature noise (primary climate axis - "latitude") ----
        FastNoiseLite tempN = new FastNoiseLite(GlobalTerrainUtils.mixSeed(seed, 0xBB67AE85));
        tempN.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2S);
        tempN.SetFrequency(EngineConfig.BIOME_TEMP_FREQ);
        tempN.SetFractalType(FastNoiseLite.FractalType.FBm);
        tempN.SetFractalOctaves(2);
        tempN.SetFractalGain(0.4f);
        tempN.SetFractalLacunarity(2.0f);

        // ---- Humidity noise (secondary climate axis) ----
        FastNoiseLite humidN = new FastNoiseLite(GlobalTerrainUtils.mixSeed(seed, 0x3C6EF372));
        humidN.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2S);
        humidN.SetFrequency(EngineConfig.BIOME_HUMID_FREQ);
        humidN.SetFractalType(FastNoiseLite.FractalType.FBm);
        humidN.SetFractalOctaves(2);
        humidN.SetFractalGain(0.4f);
        humidN.SetFractalLacunarity(2.0f);

        // ---- Continentalness noise for latitudinal variation ----
        FastNoiseLite contN = new FastNoiseLite(GlobalTerrainUtils.mixSeed(seed, 0x510E527F));
        contN.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2S);
        contN.SetFrequency(EngineConfig.BIOME_CONTINENTAL_FREQ);
        contN.SetFractalType(FastNoiseLite.FractalType.FBm);
        contN.SetFractalOctaves(2);
        contN.SetFractalGain(0.35f);
        contN.SetFractalLacunarity(2.0f);

        // ---- Swamp-specific noise for localized wet lowlands ----
        FastNoiseLite swampN = new FastNoiseLite(GlobalTerrainUtils.mixSeed(seed, 0x9B05688C));
        swampN.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        swampN.SetFrequency(EngineConfig.SWAMP_NOISE_FREQ);
        swampN.SetFractalType(FastNoiseLite.FractalType.FBm);
        swampN.SetFractalOctaves(3);
        swampN.SetFractalGain(0.5f);
        swampN.SetFractalLacunarity(2.0f);

        // ---- Transition noise for smooth biome boundaries ----
        FastNoiseLite transitionN = new FastNoiseLite(GlobalTerrainUtils.mixSeed(seed, 0x1F83D9AB));
        transitionN.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        transitionN.SetFrequency(EngineConfig.BIOME_WEIRD_FREQ);
        transitionN.SetFractalType(FastNoiseLite.FractalType.FBm);
        transitionN.SetFractalOctaves(2);
        transitionN.SetFractalGain(0.5f);
        transitionN.SetFractalLacunarity(2.0f);

        final int sea = EngineConfig.SEA_LEVEL;

        int i = 0;
        for (int z = 0; z < rect.sizeZ; z++) {
            int wz0 = rect.minZ + z;
            for (int x = 0; x < rect.sizeX; x++) {
                int wx0 = rect.minX + x;

                // Apply domain warp for organic borders
                FastNoiseLite.Vector2 p = new FastNoiseLite.Vector2(wx0, wz0);
                warp.DomainWarp(p);
                float wx = p.x;
                float wz = p.y;

                int h = heightmap.heightAtUnchecked(wx0, wz0);
                int elevationAboveSea = h - sea;

                // ========================================
                // PHASE 1: Ocean biomes (below sea level)
                // ========================================

                // Deep Ocean: significantly below sea level
                if (elevationAboveSea < -EngineConfig.DEEP_OCEAN_DEPTH) {
                    biomes[i++] = EngineConfig.BIOME_DEEP_OCEAN;
                    continue;
                }

                // Ocean: below sea level (but not deep)
                if (elevationAboveSea < 0) {
                    biomes[i++] = EngineConfig.BIOME_OCEAN;
                    continue;
                }

                // ========================================
                // PHASE 2: Land biomes (climate grid)
                // ========================================

                // Sample climate values with rotated coordinates to decorrelate
                float tx = 0.866f * wx + 0.5f * wz + 100000.0f;
                float tz = -0.5f * wx + 0.866f * wz - 100000.0f;

                float hx = -0.707f * wx + 0.707f * wz + 150000.0f;
                float hz = 0.707f * wx + 0.707f * wz - 50000.0f;

                // Get raw climate values (in range -1 to 1)
                float tempRaw = tempN.GetNoise(tx, tz);
                float humidRaw = humidN.GetNoise(hx, hz);
                float contRaw = contN.GetNoise(wx * 0.5f, wz * 0.5f);

                // Combine temperature with continentalness for latitudinal feel
                float tempCombined = tempRaw * 0.7f + contRaw * 0.3f;

                // Convert to 0-1 range with smoothing
                float temp = GlobalTerrainUtils.clamp01((tempCombined + 1.0f) * 0.5f);
                float humid = GlobalTerrainUtils.clamp01((humidRaw + 1.0f) * 0.5f);

                // Altitude cooling effect - higher areas get colder
                float altitudeCooling = elevationAboveSea / 80.0f;
                if (altitudeCooling < 0f) altitudeCooling = 0f;
                if (altitudeCooling > 1f) altitudeCooling = 1f;
                temp = GlobalTerrainUtils.clamp01(temp - altitudeCooling * EngineConfig.BIOME_ALTITUDE_COOLING);

                // Transition noise for smooth boundaries (small perturbation)
                float transition = transitionN.GetNoise(wx + 50000.0f, wz + 50000.0f) * 0.08f;
                temp = GlobalTerrainUtils.clamp01(temp + transition);
                humid = GlobalTerrainUtils.clamp01(humid + transition * 0.5f);

                // ========================================
                // SWAMP: Inland low-lying humid areas
                // Must be: low elevation, high humidity, NOT near ocean coastlines
                // ========================================
                boolean isLowElevation = elevationAboveSea <= EngineConfig.SWAMP_MAX_ELEVATION_ABOVE_SEA;
                boolean isHumid = humid >= EngineConfig.SWAMP_MIN_HUMIDITY;

                if (isLowElevation && isHumid) {
                    // Check distance from ocean - swamps must be INLAND
                    int distanceFromOcean = computeDistanceFromOcean(heightmap, rect, wx0, wz0, sea);
                    boolean isInland = distanceFromOcean >= EngineConfig.SWAMP_MIN_DISTANCE_FROM_OCEAN;

                    if (isInland) {
                        // Use swamp noise to create isolated swamp patches
                        float swampValue = GlobalTerrainUtils.to01(swampN.GetNoise(wx, wz));
                        // Temperature constraint: swamps don't appear in very cold or very hot regions
                        boolean tempSuitableForSwamp = temp > 0.30f && temp < 0.70f;

                        if (tempSuitableForSwamp && swampValue > 0.50f) {
                            biomes[i++] = EngineConfig.BIOME_SWAMP;
                            continue;
                        }
                    }
                }

                // ========================================
                // Standard climate biomes from the climate grid
                // ========================================
                boolean isNearCoast = elevationAboveSea <= 6;
                short biome = selectBiomeFromClimate(temp, humid, isNearCoast);
                biomes[i++] = biome;
            }
        }

        return new BiomeMap(rect, biomes);
    }

    /**
     * Compute the minimum distance from this position to ocean (below sea level).
     * Used to ensure swamps are inland, not near coastlines.
     */
    private int computeDistanceFromOcean(Heightmap heightmap, LayerRect rect, int wx, int wz, int sea) {
        // Check in expanding rings until we find ocean or reach max distance
        final int maxDist = EngineConfig.SWAMP_MIN_DISTANCE_FROM_OCEAN + 4;

        for (int dist = 1; dist <= maxDist; dist += 4) {
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

    /**
     * Select biome based on temperature and humidity using a climate grid.
     * Ensures logical transitions and prevents opposite biomes from neighboring.
     *
     * Climate Grid Layout:
     *
     *        DRY          MODERATE        WET
     * HOT    Desert       Savanna         Jungle
     * WARM   Savanna      Plains          Forest
     * COOL   Plains       Forest          Forest
     * COLD   Snow         Snow            Snow
     *
     * Transitions are smooth because adjacent grid cells share similar biomes.
     *
     * @param temp Temperature value (0-1)
     * @param humid Humidity value (0-1)
     * @param isCoastalZone If true, prevents jungle from appearing (coastal areas get savanna instead)
     */
    private short selectBiomeFromClimate(float temp, float humid, boolean isCoastalZone) {
        // Temperature zones
        boolean isCold = temp < EngineConfig.CLIMATE_COLD_MAX;
        boolean isCool = temp >= EngineConfig.CLIMATE_COLD_MAX && temp < EngineConfig.CLIMATE_COOL_MAX;
        boolean isWarm = temp >= EngineConfig.CLIMATE_COOL_MAX && temp < EngineConfig.CLIMATE_WARM_MAX;
        boolean isHot = temp >= EngineConfig.CLIMATE_WARM_MAX;

        // Humidity zones
        boolean isDry = humid < EngineConfig.CLIMATE_DRY_MAX;
        boolean isModerate = humid >= EngineConfig.CLIMATE_DRY_MAX && humid < EngineConfig.CLIMATE_MODERATE_MAX;
        boolean isWet = humid >= EngineConfig.CLIMATE_MODERATE_MAX;

        // Cold region - always snow (prevents desert-snow contact)
        if (isCold) {
            return EngineConfig.BIOME_SNOW;
        }

        // Cool region - transition zone
        if (isCool) {
            if (isDry) {
                return EngineConfig.BIOME_PLAINS;  // Cold-dry = plains (buffer before desert)
            } else if (isWet) {
                return EngineConfig.BIOME_FOREST;  // Cold-wet = forest
            } else {
                return EngineConfig.BIOME_FOREST;  // Cold-moderate = forest
            }
        }

        // Warm region - diverse biomes
        if (isWarm) {
            if (isDry) {
                return EngineConfig.BIOME_SAVANNA;  // Warm-dry = savanna (transition to desert)
            } else if (isModerate) {
                return EngineConfig.BIOME_PLAINS;   // Warm-moderate = plains
            } else if (isWet) {
                return EngineConfig.BIOME_FOREST;   // Warm-wet = forest
            }
        }

        // Hot region - extreme biomes
        if (isHot) {
            if (isDry) {
                return EngineConfig.BIOME_DESERT;   // Hot-dry = desert
            } else if (isModerate) {
                return EngineConfig.BIOME_SAVANNA;  // Hot-moderate = savanna
            } else if (isWet) {
                // Jungle only on inland areas, not coastal zones
                if (isCoastalZone) {
                    return EngineConfig.BIOME_SAVANNA;  // Hot-wet coastal = savanna (buffer for jungle)
                }
                return EngineConfig.BIOME_JUNGLE;   // Hot-wet inland = jungle
            }
        }

        // Fallback (shouldn't reach here)
        return EngineConfig.BIOME_PLAINS;
    }
}
