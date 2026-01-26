package org.aouessar.core.gen.impl;

import org.aouessar.core.gen.WorldGenerator;
import org.aouessar.core.math.GlobalTerrainUtils;
import org.aouessar.core.noise.FastNoiseLite;
import org.aouessar.core.world.layers.Heightmap;
import org.aouessar.core.world.layers.LayerRect;
import org.aouessar.shared.EngineConfig;

public final class SimpleWorldGenerator implements WorldGenerator {

    @Override
    public Heightmap generateHeightmap(long seed, LayerRect rect) {
        int n = rect.sizeX * rect.sizeZ;
        int[] heights = new int[n];

        // --- Noise stack (all deterministic from seed) ---
        FastNoiseLite warp = new FastNoiseLite(GlobalTerrainUtils.mixSeed(seed, 0xA1B2C3D4));
        warp.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        warp.SetFrequency(EngineConfig.TERRAIN_WARP_FREQ);
        warp.SetFractalType(FastNoiseLite.FractalType.DomainWarpProgressive);
        warp.SetFractalOctaves(3);
        warp.SetFractalGain(0.5f);
        warp.SetFractalLacunarity(2.0f);
        warp.SetDomainWarpAmp(EngineConfig.TERRAIN_WARP_AMP_BLOCKS);

        FastNoiseLite continents = new FastNoiseLite(GlobalTerrainUtils.mixSeed(seed, 0x11111111));
        continents.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        continents.SetFrequency(EngineConfig.TERRAIN_CONTINENT_FREQ);
        continents.SetFractalType(FastNoiseLite.FractalType.FBm);
        continents.SetFractalOctaves(3);  // was 5 - fewer octaves = smoother, fewer small islands
        continents.SetFractalGain(0.4f);  // was 0.5 - lower gain = less high-frequency detail
        continents.SetFractalLacunarity(2.0f);

        FastNoiseLite large = new FastNoiseLite(GlobalTerrainUtils.mixSeed(seed, 0x12121212));
        large.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        large.SetFrequency(EngineConfig.TERRAIN_LARGE_FREQ);
        large.SetFractalType(FastNoiseLite.FractalType.FBm);
        large.SetFractalOctaves(4);
        large.SetFractalGain(0.5f);
        large.SetFractalLacunarity(2.0f);

        FastNoiseLite erosion = new FastNoiseLite(GlobalTerrainUtils.mixSeed(seed, 0x22222222));
        erosion.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        erosion.SetFrequency(EngineConfig.TERRAIN_LARGE_FREQ);
        erosion.SetFractalType(FastNoiseLite.FractalType.FBm);
        erosion.SetFractalOctaves(4);
        erosion.SetFractalGain(0.5f);
        erosion.SetFractalLacunarity(2.0f);

        FastNoiseLite ridges = new FastNoiseLite(GlobalTerrainUtils.mixSeed(seed, 0x33333333));
        ridges.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        ridges.SetFrequency(EngineConfig.TERRAIN_RIDGE_FREQ);
        ridges.SetFractalType(FastNoiseLite.FractalType.Ridged);
        ridges.SetFractalOctaves(4);
        ridges.SetFractalGain(0.55f);
        ridges.SetFractalLacunarity(2.05f);

        FastNoiseLite detail = new FastNoiseLite(GlobalTerrainUtils.mixSeed(seed, 0x44444444));
        detail.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        detail.SetFrequency(EngineConfig.TERRAIN_DETAIL_FREQ);
        detail.SetFractalType(FastNoiseLite.FractalType.FBm);
        detail.SetFractalOctaves(6);
        detail.SetFractalGain(0.5f);
        detail.SetFractalLacunarity(2.0f);

        // NEW (additive): rare range booster mask (does NOT gate normal mountains)
        FastNoiseLite range = new FastNoiseLite(GlobalTerrainUtils.mixSeed(seed, 0x55555555));
        range.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        range.SetFrequency(EngineConfig.TERRAIN_RANGE_FREQ);
        range.SetFractalType(FastNoiseLite.FractalType.Ridged);
        range.SetFractalOctaves(3);
        range.SetFractalGain(0.55f);
        range.SetFractalLacunarity(2.05f);

        // NEW (additive): rare peaks
        FastNoiseLite peaks = new FastNoiseLite(GlobalTerrainUtils.mixSeed(seed, 0x66666666));
        peaks.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        peaks.SetFrequency(EngineConfig.TERRAIN_PEAK_FREQ);
        peaks.SetFractalType(FastNoiseLite.FractalType.FBm);
        peaks.SetFractalOctaves(3);
        peaks.SetFractalGain(0.5f);
        peaks.SetFractalLacunarity(2.0f);

        final int sea = EngineConfig.SEA_LEVEL;

        int i = 0;
        for (int z = 0; z < rect.sizeZ; z++) {
            int wz0 = rect.minZ + z;
            for (int x = 0; x < rect.sizeX; x++) {
                int wx0 = rect.minX + x;

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

                // -------------------- Mountains (BASE: unchanged) --------------------

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

                // IMPORTANT: keep your original mountain mask (no range gating!)
                float mountainMask = land * ridgePresence * erosionSuppress;

                float mountains = (float) Math.pow(ridge01, EngineConfig.TERRAIN_RIDGE_EXP)
                        * EngineConfig.TERRAIN_MOUNTAIN_AMPLITUDE * mountainMask;

                // -------------------- NEW: Rare long ranges (additive boost) --------------------

                // A macro mask that occasionally boosts mountains into big belts.
                float range01 = GlobalTerrainUtils.clamp01(Math.abs(range.GetNoise(wx, wz)));
                float rangePresence = GlobalTerrainUtils.smoothstep(
                        EngineConfig.TERRAIN_RANGE_MIN,
                        EngineConfig.TERRAIN_RANGE_MAX,
                        range01
                );
                rangePresence = (float) Math.pow(rangePresence, EngineConfig.TERRAIN_RANGE_POWER);

                // Boost mostly inland (optional but helps avoid insane coastal walls).
                float rangeBoost = rangePresence * inland;

                // Add extra height on top of the *existing* mountains (no behavior loss elsewhere)
                float rangeMountains = (float) Math.pow(ridge01, EngineConfig.TERRAIN_RIDGE_EXP)
                        * EngineConfig.TERRAIN_RANGE_EXTRA_AMPLITUDE
                        * mountainMask
                        * rangeBoost;

                mountains += rangeMountains;

                // -------------------- NEW: Ultra-rare mega peaks (additive) --------------------

                float peak01 = (peaks.GetNoise(wx, wz) + 1.0f) * 0.5f; // [0..1]
                float peakPresence = GlobalTerrainUtils.smoothstep(
                        EngineConfig.TERRAIN_PEAK_THRESHOLD,
                        1.0f,
                        peak01
                );
                peakPresence = (float) Math.pow(peakPresence, EngineConfig.TERRAIN_PEAK_POWER);

                // Keep peaks on ridge spines, inside boosted ranges, and inside mountains
                float megaPeaks = peakPresence
                        * EngineConfig.TERRAIN_PEAK_AMPLITUDE
                        * mountainMask
                        * ridgePresence
                        * rangeBoost;

                mountains += megaPeaks;

                // -------------------- Oceans with varied depth --------------------

                // offshore: 0 = land, 1 = deep ocean
                float offshore = 1.0f - land;

                // Create a very gradual depth gradient from coast to deep ocean
                // Use cubic curve (offshore^3) for much slower initial descent - stays shallow longer
                float shallowGradient = offshore * offshore;  // Quadratic for initial shallow zone
                float deepGradient = offshore * offshore * offshore;  // Cubic for deep ocean transition

                // Shallow coastal zone - very gradual slope near shore
                // Only reaches ~12 blocks deep even at offshore=0.5
                float shallowDepth = EngineConfig.TERRAIN_OCEAN_BASE_DEPTH * shallowGradient;

                // Deep ocean depth - only kicks in significantly when offshore > 0.6
                // Uses cubic curve so it stays shallow much longer
                float deepTransition = GlobalTerrainUtils.smoothstep(0.5f, 0.85f, offshore);
                float deepDepth = EngineConfig.TERRAIN_OCEAN_EXTRA_DEPTH * deepTransition * deepGradient;

                // Deep ocean floor variation - only in truly deep areas (offshore > 0.75)
                float deepOceanFactor = GlobalTerrainUtils.smoothstep(0.75f, 0.95f, offshore);
                float deepFloorVariation = deepOceanFactor * EngineConfig.TERRAIN_DEEP_OCEAN_EXTRA_DEPTH
                        * (0.5f + 0.5f * L); // Use large noise for underwater hills/valleys

                // Rare ocean trenches only in the deepest areas (offshore > 0.85)
                float trenchNoise = GlobalTerrainUtils.clamp01((r + 1.0f) * 0.5f); // Reuse ridge noise
                float trenchFactor = GlobalTerrainUtils.smoothstep(0.85f, 0.98f, offshore)
                        * GlobalTerrainUtils.smoothstep(0.7f, 0.95f, trenchNoise);
                float trenchDepth = trenchFactor * EngineConfig.TERRAIN_OCEAN_TRENCH_DEPTH;

                // Seabed detail variation (underwater terrain texture) - reduced near shore
                float seabedDetail = shallowGradient * d * EngineConfig.TERRAIN_SEABED_VARIATION;

                // Large-scale underwater terrain variation - also reduced near shore
                float underwaterLarge = shallowGradient * L * EngineConfig.TERRAIN_OCEAN_LARGE_VARIATION;

                // Combine all ocean depth components
                float totalOceanDepth = shallowDepth + deepDepth + deepFloorVariation + trenchDepth + seabedDetail;

                // Ocean floor height (lower = deeper)
                float oceanHeight = sea - totalOceanDepth + underwaterLarge;

                // Ensure deep ocean has minimum depth only when truly far offshore
                if (offshore > 0.8f) {
                    float minDeepHeight = sea - EngineConfig.TERRAIN_DEEP_OCEAN_MIN_DEPTH;
                    float enforceAmount = GlobalTerrainUtils.smoothstep(0.8f, 0.95f, offshore);
                    float targetHeight = minDeepHeight + underwaterLarge * 0.3f;
                    oceanHeight = org.joml.Math.lerp(oceanHeight, Math.min(oceanHeight, targetHeight), enforceAmount);
                }

                float landHeight = sea + uplift + rolling + hills + mountains;

                // Smooth transition between land and ocean
                float shoreBlend = GlobalTerrainUtils.smoothstep(0.35f, 0.65f, land);
                float height = org.joml.Math.lerp(oceanHeight, landHeight, shoreBlend);

                int h = Math.round(height);
                if (h < EngineConfig.MIN_Y) h = EngineConfig.MIN_Y;
                if (h > EngineConfig.MAX_Y) h = EngineConfig.MAX_Y;

                heights[i++] = h;
            }
        }

        return new Heightmap(rect, heights);
    }
}