package org.aouessar.core.gen.impl;

import org.aouessar.core.math.GlobalTerrainUtils;
import org.aouessar.core.noise.FastNoiseLite;
import org.aouessar.shared.EngineConfig;

/**
 * Per-column terrain height function.
 * <p>
 * This is THE single source of truth for terrain shape. Both the full-resolution
 * region pipeline ({@link SimpleWorldGenerator}) and the far-field LOD sampler
 * ({@link LodWorldSampler}) evaluate this same function, which guarantees that
 * distant LOD terrain lines up exactly with near-field chunks.
 * <p>
 * Thread safety: the FastNoiseLite instances are configured once in the
 * constructor and only read afterwards, so a single instance can be shared
 * across worker threads.
 */
public final class TerrainColumnSampler {

    private final FastNoiseLite warp;
    private final FastNoiseLite continents;
    private final FastNoiseLite large;
    private final FastNoiseLite erosion;
    private final FastNoiseLite ridges;
    private final FastNoiseLite detail;
    private final FastNoiseLite range;
    private final FastNoiseLite peaks;
    private final FastNoiseLite oceanIslands;
    private final RiverColumnSampler rivers;

    public TerrainColumnSampler(long seed) {
        rivers = new RiverColumnSampler(seed);
        warp = new FastNoiseLite(GlobalTerrainUtils.mixSeed(seed, 0xA1B2C3D4));
        warp.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        warp.SetFrequency(EngineConfig.TERRAIN_WARP_FREQ);
        warp.SetFractalType(FastNoiseLite.FractalType.DomainWarpProgressive);
        warp.SetFractalOctaves(3);
        warp.SetFractalGain(0.5f);
        warp.SetFractalLacunarity(2.0f);
        warp.SetDomainWarpAmp(EngineConfig.TERRAIN_WARP_AMP_BLOCKS);

        continents = new FastNoiseLite(GlobalTerrainUtils.mixSeed(seed, 0x11111111));
        continents.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        continents.SetFrequency(EngineConfig.TERRAIN_CONTINENT_FREQ);
        continents.SetFractalType(FastNoiseLite.FractalType.FBm);
        continents.SetFractalOctaves(3);
        continents.SetFractalGain(0.4f);
        continents.SetFractalLacunarity(2.0f);

        large = new FastNoiseLite(GlobalTerrainUtils.mixSeed(seed, 0x12121212));
        large.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        large.SetFrequency(EngineConfig.TERRAIN_LARGE_FREQ);
        large.SetFractalType(FastNoiseLite.FractalType.FBm);
        large.SetFractalOctaves(4);
        large.SetFractalGain(0.5f);
        large.SetFractalLacunarity(2.0f);

        erosion = new FastNoiseLite(GlobalTerrainUtils.mixSeed(seed, 0x22222222));
        erosion.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        erosion.SetFrequency(EngineConfig.TERRAIN_LARGE_FREQ);
        erosion.SetFractalType(FastNoiseLite.FractalType.FBm);
        erosion.SetFractalOctaves(4);
        erosion.SetFractalGain(0.5f);
        erosion.SetFractalLacunarity(2.0f);

        ridges = new FastNoiseLite(GlobalTerrainUtils.mixSeed(seed, 0x33333333));
        ridges.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        ridges.SetFrequency(EngineConfig.TERRAIN_RIDGE_FREQ);
        ridges.SetFractalType(FastNoiseLite.FractalType.Ridged);
        ridges.SetFractalOctaves(4);
        ridges.SetFractalGain(0.55f);
        ridges.SetFractalLacunarity(2.05f);

        detail = new FastNoiseLite(GlobalTerrainUtils.mixSeed(seed, 0x44444444));
        detail.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        detail.SetFrequency(EngineConfig.TERRAIN_DETAIL_FREQ);
        detail.SetFractalType(FastNoiseLite.FractalType.FBm);
        detail.SetFractalOctaves(6);
        detail.SetFractalGain(0.5f);
        detail.SetFractalLacunarity(2.0f);

        // Rare range booster mask (additive, does NOT gate normal mountains)
        range = new FastNoiseLite(GlobalTerrainUtils.mixSeed(seed, 0x55555555));
        range.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        range.SetFrequency(EngineConfig.TERRAIN_RANGE_FREQ);
        range.SetFractalType(FastNoiseLite.FractalType.Ridged);
        range.SetFractalOctaves(3);
        range.SetFractalGain(0.55f);
        range.SetFractalLacunarity(2.05f);

        // Ultra-rare mega peaks (additive)
        peaks = new FastNoiseLite(GlobalTerrainUtils.mixSeed(seed, 0x66666666));
        peaks.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        peaks.SetFrequency(EngineConfig.TERRAIN_PEAK_FREQ);
        peaks.SetFractalType(FastNoiseLite.FractalType.FBm);
        peaks.SetFractalOctaves(3);
        peaks.SetFractalGain(0.5f);
        peaks.SetFractalLacunarity(2.0f);

        // Rare deep ocean islands (volcanic islands like Maldives, Bora Bora)
        oceanIslands = new FastNoiseLite(GlobalTerrainUtils.mixSeed(seed, 0x77777777));
        oceanIslands.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        oceanIslands.SetFrequency(EngineConfig.TERRAIN_OCEAN_ISLAND_FREQ);
        oceanIslands.SetFractalType(FastNoiseLite.FractalType.FBm);
        oceanIslands.SetFractalOctaves(3);
        oceanIslands.SetFractalGain(0.5f);
        oceanIslands.SetFractalLacunarity(2.0f);
    }

    /** Terrain surface world Y at (wx, wz), clamped to MIN_Y..MAX_Y. */
    public int heightAt(int wx0, int wz0) {
        final int sea = EngineConfig.SEA_LEVEL;

        // Domain-warped sampling coords
        FastNoiseLite.Vector2 p = new FastNoiseLite.Vector2(wx0, wz0);
        warp.DomainWarp(p);
        float wx = p.x;
        float wz = p.y;

        // Fields in [-1..1]
        float cRaw = continents.GetNoise(wx, wz);
        float c = cRaw * EngineConfig.TERRAIN_CONTINENT_CONTRAST + EngineConfig.TERRAIN_CONTINENT_BIAS;
        c = GlobalTerrainUtils.clamp(c, -1.0f, 1.0f);

        float L = large.GetNoise(wx, wz);
        float e = erosion.GetNoise(wx, wz);
        float r = ridges.GetNoise(wx, wz);
        float d = detail.GetNoise(wx, wz);

        // Land factor [0..1] with smooth coasts
        float land = GlobalTerrainUtils.smoothstep(
                EngineConfig.TERRAIN_COAST_OCEAN,
                EngineConfig.TERRAIN_COAST_LAND,
                c
        );

        // Inland uplift
        float inland = GlobalTerrainUtils.smoothstep(
                EngineConfig.TERRAIN_INLAND_START,
                EngineConfig.TERRAIN_INLAND_FULL,
                c
        );
        float uplift = inland * inland * EngineConfig.TERRAIN_BASE_LAND_UPLIFT;

        // Rolling elevation + hills on land
        float rolling = L * EngineConfig.TERRAIN_LARGE_AMPLITUDE * land;
        float hills   = d * EngineConfig.TERRAIN_HILL_AMPLITUDE * land;

        // -------------------- Mountains (base) --------------------

        float ridge01 = GlobalTerrainUtils.clamp01(Math.abs(r));
        float ridgePresence = GlobalTerrainUtils.smoothstep(
                EngineConfig.TERRAIN_RIDGE_MIN,
                EngineConfig.TERRAIN_RIDGE_MAX,
                ridge01
        );

        float erosion01 = (e + 1.0f) * 0.5f;
        float erosionSuppress = 1.0f - GlobalTerrainUtils.smoothstep(
                EngineConfig.TERRAIN_EROSION_MIN,
                EngineConfig.TERRAIN_EROSION_MAX,
                erosion01
        );

        float mountainMask = land * ridgePresence * erosionSuppress;

        float mountains = (float) Math.pow(ridge01, EngineConfig.TERRAIN_RIDGE_EXP)
                * EngineConfig.TERRAIN_MOUNTAIN_AMPLITUDE * mountainMask;

        // -------------------- Rare long ranges (additive boost) --------------------

        float range01 = GlobalTerrainUtils.clamp01(Math.abs(range.GetNoise(wx, wz)));
        float rangePresence = GlobalTerrainUtils.smoothstep(
                EngineConfig.TERRAIN_RANGE_MIN,
                EngineConfig.TERRAIN_RANGE_MAX,
                range01
        );
        rangePresence = (float) Math.pow(rangePresence, EngineConfig.TERRAIN_RANGE_POWER);

        float rangeBoost = rangePresence * inland;

        float rangeMountains = (float) Math.pow(ridge01, EngineConfig.TERRAIN_RIDGE_EXP)
                * EngineConfig.TERRAIN_RANGE_EXTRA_AMPLITUDE
                * mountainMask
                * rangeBoost;

        mountains += rangeMountains;

        // -------------------- Ultra-rare mega peaks (additive) --------------------

        float peak01 = (peaks.GetNoise(wx, wz) + 1.0f) * 0.5f;
        float peakPresence = GlobalTerrainUtils.smoothstep(
                EngineConfig.TERRAIN_PEAK_THRESHOLD,
                1.0f,
                peak01
        );
        peakPresence = (float) Math.pow(peakPresence, EngineConfig.TERRAIN_PEAK_POWER);

        float megaPeaks = peakPresence
                * EngineConfig.TERRAIN_PEAK_AMPLITUDE
                * mountainMask
                * ridgePresence
                * rangeBoost;

        mountains += megaPeaks;

        // -------------------- Oceans with varied depth --------------------

        // offshore: 0 = land, 1 = deep ocean
        float offshore = 1.0f - land;

        float shallowGradient = offshore * offshore;
        float deepGradient = offshore * offshore * offshore;

        float shallowDepth = EngineConfig.TERRAIN_OCEAN_BASE_DEPTH * shallowGradient;

        float deepTransition = GlobalTerrainUtils.smoothstep(0.5f, 0.85f, offshore);
        float deepDepth = EngineConfig.TERRAIN_OCEAN_EXTRA_DEPTH * deepTransition * deepGradient;

        float deepOceanFactor = GlobalTerrainUtils.smoothstep(0.75f, 0.95f, offshore);
        float deepFloorVariation = deepOceanFactor * EngineConfig.TERRAIN_DEEP_OCEAN_EXTRA_DEPTH
                * (0.5f + 0.5f * L);

        float trenchNoise = GlobalTerrainUtils.clamp01((r + 1.0f) * 0.5f);
        float trenchFactor = GlobalTerrainUtils.smoothstep(0.85f, 0.98f, offshore)
                * GlobalTerrainUtils.smoothstep(0.7f, 0.95f, trenchNoise);
        float trenchDepth = trenchFactor * EngineConfig.TERRAIN_OCEAN_TRENCH_DEPTH;

        float seabedDetail = shallowGradient * d * EngineConfig.TERRAIN_SEABED_VARIATION;

        float underwaterLarge = shallowGradient * L * EngineConfig.TERRAIN_OCEAN_LARGE_VARIATION;

        float totalOceanDepth = shallowDepth + deepDepth + deepFloorVariation + trenchDepth + seabedDetail;

        float oceanHeight = sea - totalOceanDepth + underwaterLarge;

        if (offshore > 0.8f) {
            float minDeepHeight = sea - EngineConfig.TERRAIN_DEEP_OCEAN_MIN_DEPTH;
            float enforceAmount = GlobalTerrainUtils.smoothstep(0.8f, 0.95f, offshore);
            float targetHeight = minDeepHeight + underwaterLarge * 0.3f;
            oceanHeight = org.joml.Math.lerp(oceanHeight, Math.min(oceanHeight, targetHeight), enforceAmount);
        }

        // -------------------- Rare deep ocean islands (volcanic) --------------------

        if (offshore > 0.92f) {
            float islandNoise = (oceanIslands.GetNoise(wx, wz) + 1.0f) * 0.5f;

            if (islandNoise > EngineConfig.TERRAIN_OCEAN_ISLAND_THRESHOLD) {
                float islandIntensity = (islandNoise - EngineConfig.TERRAIN_OCEAN_ISLAND_THRESHOLD)
                        / (1.0f - EngineConfig.TERRAIN_OCEAN_ISLAND_THRESHOLD);

                islandIntensity = (float) Math.pow(islandIntensity, EngineConfig.TERRAIN_OCEAN_ISLAND_POWER);

                float deepOceanMask = GlobalTerrainUtils.smoothstep(0.92f, 0.98f, offshore);
                islandIntensity *= deepOceanMask;

                float islandRise = islandIntensity
                        * (EngineConfig.TERRAIN_OCEAN_ISLAND_BASE + EngineConfig.TERRAIN_OCEAN_ISLAND_HEIGHT);

                float islandVariation = 1.0f + d * 0.2f;
                islandRise *= islandVariation;

                oceanHeight += islandRise;
            }
        }

        float landHeight = sea + uplift + rolling + hills + mountains;

        // Smooth transition between land and ocean
        float shoreBlend = GlobalTerrainUtils.smoothstep(0.35f, 0.65f, land);
        float height = org.joml.Math.lerp(oceanHeight, landHeight, shoreBlend);

        // -------------------- River valleys (sea-level water) --------------------
        // Terrain is blended DOWN toward sea level across river valleys, so
        // water is always at sea level (like vanilla Minecraft). Valleys fade
        // with elevation: lowland rivers are wide and wet, foothill rivers
        // become shallow dry gullies, mountains resist entirely.
        // Uses the un-warped column coordinates so the same function is
        // evaluated by near-field regions and far-field LOD tiles alike.
        if (height > sea - 4f) {
            float valley = rivers.valley01(wx0, wz0);
            if (valley > 0f) {
                float elevFade = 1f - GlobalTerrainUtils.smoothstep(
                        EngineConfig.RIVER_LOWLAND_FULL,
                        EngineConfig.RIVER_MAX_ELEVATION_ABOVE_SEA,
                        height - sea);

                if (elevFade > 0f) {
                    float channel = RiverColumnSampler.channel01(valley);
                    // Valley walls slope from the surrounding terrain down to
                    // the banks; the channel core dips below sea level.
                    float target = org.joml.Math.lerp(
                            sea + EngineConfig.RIVER_BANK_HEIGHT,
                            sea - EngineConfig.RIVER_BED_DEPTH,
                            channel);

                    float blend = GlobalTerrainUtils.smoothstep(0f, 0.55f, valley) * elevFade;
                    if (height > target) {
                        height = org.joml.Math.lerp(height, target, blend);
                    }
                }
            }
        }

        int h = Math.round(height);
        if (h < EngineConfig.MIN_Y) h = EngineConfig.MIN_Y;
        if (h > EngineConfig.MAX_Y) h = EngineConfig.MAX_Y;
        return h;
    }
}
