package org.aouessar.core.gen.impl;

import org.aouessar.core.math.GlobalTerrainUtils;
import org.aouessar.core.noise.FastNoiseLite;
import org.aouessar.shared.EngineConfig;

/**
 * Per-column climate + biome classification.
 * <p>
 * Extracted from {@link DefaultBiomeGenerator} so the far-field LOD sampler
 * ({@link LodWorldSampler}) classifies biomes with exactly the same noise
 * fields and thresholds as the region pipeline.
 * <p>
 * The swamp "must be inland" refinement needs a heightmap neighborhood, so it
 * is injected via {@link InlandTest}; the LOD path passes a test that always
 * fails (swamps degrade to their climate-grid biome, which shares the same
 * surface palette at distance).
 * <p>
 * Thread safety: noise instances are configured once and only read afterwards.
 */
public final class ClimateColumnSampler {

    /** Decides whether (wx, wz) is far enough from the ocean for a swamp. */
    @FunctionalInterface
    public interface InlandTest {
        boolean isInland(int wx, int wz);
    }

    public static final InlandTest NEVER_INLAND = (wx, wz) -> false;

    private final FastNoiseLite warp;
    private final FastNoiseLite tempN;
    private final FastNoiseLite humidN;
    private final FastNoiseLite contN;
    private final FastNoiseLite swampN;
    private final FastNoiseLite transitionN;

    public ClimateColumnSampler(long seed) {
        // ---- Domain Warp for organic, wavy borders ----
        warp = new FastNoiseLite(GlobalTerrainUtils.mixSeed(seed, 0x6A09E667));
        warp.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        warp.SetFrequency(EngineConfig.BIOME_WARP_FREQ);
        warp.SetFractalType(FastNoiseLite.FractalType.DomainWarpProgressive);
        warp.SetFractalOctaves(3);
        warp.SetFractalGain(0.5f);
        warp.SetFractalLacunarity(2.0f);
        warp.SetDomainWarpAmp(EngineConfig.BIOME_WARP_AMP_BLOCKS);

        // ---- Temperature noise (primary climate axis - "latitude") ----
        tempN = new FastNoiseLite(GlobalTerrainUtils.mixSeed(seed, 0xBB67AE85));
        tempN.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2S);
        tempN.SetFrequency(EngineConfig.BIOME_TEMP_FREQ);
        tempN.SetFractalType(FastNoiseLite.FractalType.FBm);
        tempN.SetFractalOctaves(2);
        tempN.SetFractalGain(0.4f);
        tempN.SetFractalLacunarity(2.0f);

        // ---- Humidity noise (secondary climate axis) ----
        humidN = new FastNoiseLite(GlobalTerrainUtils.mixSeed(seed, 0x3C6EF372));
        humidN.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2S);
        humidN.SetFrequency(EngineConfig.BIOME_HUMID_FREQ);
        humidN.SetFractalType(FastNoiseLite.FractalType.FBm);
        humidN.SetFractalOctaves(2);
        humidN.SetFractalGain(0.4f);
        humidN.SetFractalLacunarity(2.0f);

        // ---- Continentalness noise for latitudinal variation ----
        contN = new FastNoiseLite(GlobalTerrainUtils.mixSeed(seed, 0x510E527F));
        contN.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2S);
        contN.SetFrequency(EngineConfig.BIOME_CONTINENTAL_FREQ);
        contN.SetFractalType(FastNoiseLite.FractalType.FBm);
        contN.SetFractalOctaves(2);
        contN.SetFractalGain(0.35f);
        contN.SetFractalLacunarity(2.0f);

        // ---- Swamp-specific noise for localized wet lowlands ----
        swampN = new FastNoiseLite(GlobalTerrainUtils.mixSeed(seed, 0x9B05688C));
        swampN.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        swampN.SetFrequency(EngineConfig.SWAMP_NOISE_FREQ);
        swampN.SetFractalType(FastNoiseLite.FractalType.FBm);
        swampN.SetFractalOctaves(3);
        swampN.SetFractalGain(0.5f);
        swampN.SetFractalLacunarity(2.0f);

        // ---- Transition noise for smooth biome boundaries ----
        transitionN = new FastNoiseLite(GlobalTerrainUtils.mixSeed(seed, 0x1F83D9AB));
        transitionN.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        transitionN.SetFrequency(EngineConfig.BIOME_WEIRD_FREQ);
        transitionN.SetFractalType(FastNoiseLite.FractalType.FBm);
        transitionN.SetFractalOctaves(2);
        transitionN.SetFractalGain(0.5f);
        transitionN.SetFractalLacunarity(2.0f);
    }

    /**
     * Biome id for a column, given its terrain surface height (world Y).
     * Ocean biomes come purely from height; land biomes from the climate grid.
     */
    public short biomeAt(int wx0, int wz0, int height, InlandTest inlandTest) {
        final int sea = EngineConfig.SEA_LEVEL;
        int elevationAboveSea = height - sea;

        // ---- Phase 1: ocean biomes (below sea level) ----
        if (elevationAboveSea < -EngineConfig.DEEP_OCEAN_DEPTH) {
            return EngineConfig.BIOME_DEEP_OCEAN;
        }
        if (elevationAboveSea < 0) {
            return EngineConfig.BIOME_OCEAN;
        }

        // ---- Phase 2: land biomes (climate grid) ----

        // Apply domain warp for organic borders
        FastNoiseLite.Vector2 p = new FastNoiseLite.Vector2(wx0, wz0);
        warp.DomainWarp(p);
        float wx = p.x;
        float wz = p.y;

        // Sample climate values with rotated coordinates to decorrelate
        float tx = 0.866f * wx + 0.5f * wz + 100000.0f;
        float tz = -0.5f * wx + 0.866f * wz - 100000.0f;

        float hx = -0.707f * wx + 0.707f * wz + 150000.0f;
        float hz = 0.707f * wx + 0.707f * wz - 50000.0f;

        float tempRaw = tempN.GetNoise(tx, tz);
        float humidRaw = humidN.GetNoise(hx, hz);
        float contRaw = contN.GetNoise(wx * 0.5f, wz * 0.5f);

        // Combine temperature with continentalness for latitudinal feel
        float tempCombined = tempRaw * 0.7f + contRaw * 0.3f;

        float temp = GlobalTerrainUtils.clamp01((tempCombined + 1.0f) * 0.5f);
        float humid = GlobalTerrainUtils.clamp01((humidRaw + 1.0f) * 0.5f);

        // Altitude cooling effect - higher areas get colder
        float altitudeCooling = GlobalTerrainUtils.clamp01(elevationAboveSea / 80.0f);
        temp = GlobalTerrainUtils.clamp01(temp - altitudeCooling * EngineConfig.BIOME_ALTITUDE_COOLING);

        // Transition noise for smooth boundaries (small perturbation)
        float transition = transitionN.GetNoise(wx + 50000.0f, wz + 50000.0f) * 0.08f;
        temp = GlobalTerrainUtils.clamp01(temp + transition);
        humid = GlobalTerrainUtils.clamp01(humid + transition * 0.5f);

        // ---- Swamp: inland low-lying humid areas ----
        boolean isLowElevation = elevationAboveSea <= EngineConfig.SWAMP_MAX_ELEVATION_ABOVE_SEA;
        boolean isHumid = humid >= EngineConfig.SWAMP_MIN_HUMIDITY;

        if (isLowElevation && isHumid && inlandTest.isInland(wx0, wz0)) {
            float swampValue = GlobalTerrainUtils.to01(swampN.GetNoise(wx, wz));
            boolean tempSuitableForSwamp = temp > 0.30f && temp < 0.70f;

            if (tempSuitableForSwamp && swampValue > 0.50f) {
                return EngineConfig.BIOME_SWAMP;
            }
        }

        // ---- Standard climate biomes from the climate grid ----
        boolean isNearCoast = elevationAboveSea <= 6;
        return selectBiomeFromClimate(temp, humid, isNearCoast);
    }

    /**
     * Select biome based on temperature and humidity using a climate grid.
     *
     *        DRY          MODERATE        WET
     * HOT    Desert       Savanna         Jungle
     * WARM   Savanna      Plains          Forest
     * COOL   Plains       Forest          Forest
     * COLD   Snow         Snow            Snow
     */
    private short selectBiomeFromClimate(float temp, float humid, boolean isCoastalZone) {
        boolean isCold = temp < EngineConfig.CLIMATE_COLD_MAX;
        boolean isCool = temp >= EngineConfig.CLIMATE_COLD_MAX && temp < EngineConfig.CLIMATE_COOL_MAX;
        boolean isWarm = temp >= EngineConfig.CLIMATE_COOL_MAX && temp < EngineConfig.CLIMATE_WARM_MAX;
        boolean isHot = temp >= EngineConfig.CLIMATE_WARM_MAX;

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
                return EngineConfig.BIOME_PLAINS;   // buffer before desert
            }
            return EngineConfig.BIOME_FOREST;       // cool-moderate/wet = forest
        }

        // Warm region - diverse biomes
        if (isWarm) {
            if (isDry) return EngineConfig.BIOME_SAVANNA;
            if (isModerate) return EngineConfig.BIOME_PLAINS;
            if (isWet) return EngineConfig.BIOME_FOREST;
        }

        // Hot region - extreme biomes
        if (isHot) {
            if (isDry) return EngineConfig.BIOME_DESERT;
            if (isModerate) return EngineConfig.BIOME_SAVANNA;
            if (isWet) {
                // Jungle only inland, coastal hot-wet gets savanna as a buffer
                return isCoastalZone ? EngineConfig.BIOME_SAVANNA : EngineConfig.BIOME_JUNGLE;
            }
        }

        // Fallback (shouldn't reach here)
        return EngineConfig.BIOME_PLAINS;
    }
}
