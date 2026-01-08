package org.aouessar.core.gen.impl;

import org.aouessar.core.gen.BiomeGenerator;
import org.aouessar.core.noise.FastNoiseLite;
import org.aouessar.core.world.layers.BiomeMap;
import org.aouessar.core.world.layers.Heightmap;
import org.aouessar.core.world.layers.LayerRect;
import org.aouessar.shared.EngineConfig;

public final class DefaultBiomeGenerator implements BiomeGenerator {

    @Override
    public BiomeMap generateBiomes(long seed, Heightmap heightmap) {
        LayerRect rect = heightmap.rect();
        int n = rect.sizeX * rect.sizeZ;
        short[] biomes = new short[n];

        // Domain warp to make biome borders more natural
        FastNoiseLite warp = new FastNoiseLite(GlobalTerrainUtils.mixSeed(seed, 0x6A09E667));
        warp.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        warp.SetFrequency(EngineConfig.BIOME_WARP_FREQ);
        warp.SetFractalType(FastNoiseLite.FractalType.DomainWarpProgressive);
        warp.SetFractalOctaves(2);
        warp.SetFractalGain(0.5f);
        warp.SetFractalLacunarity(2.0f);
        warp.SetDomainWarpAmp(EngineConfig.BIOME_WARP_AMP_BLOCKS);

        FastNoiseLite tempN = new FastNoiseLite(GlobalTerrainUtils.mixSeed(seed, 0xBB67AE85));
        tempN.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        tempN.SetFrequency(EngineConfig.BIOME_TEMP_FREQ);
        tempN.SetFractalType(FastNoiseLite.FractalType.FBm);
        tempN.SetFractalOctaves(4);
        tempN.SetFractalGain(0.5f);
        tempN.SetFractalLacunarity(2.0f);

        FastNoiseLite humidN = new FastNoiseLite(GlobalTerrainUtils.mixSeed(seed, 0x3C6EF372));
        humidN.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        humidN.SetFrequency(EngineConfig.BIOME_HUMID_FREQ);
        humidN.SetFractalType(FastNoiseLite.FractalType.FBm);
        humidN.SetFractalOctaves(4);
        humidN.SetFractalGain(0.5f);
        humidN.SetFractalLacunarity(2.0f);

        FastNoiseLite weirdN = new FastNoiseLite(GlobalTerrainUtils.mixSeed(seed, 0xA54FF53A));
        weirdN.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        weirdN.SetFrequency(EngineConfig.BIOME_WEIRD_FREQ);
        weirdN.SetFractalType(FastNoiseLite.FractalType.FBm);
        weirdN.SetFractalOctaves(3);
        weirdN.SetFractalGain(0.5f);
        weirdN.SetFractalLacunarity(2.0f);

        FastNoiseLite blendSel = new FastNoiseLite(GlobalTerrainUtils.mixSeed(seed, 0xD00DFEED));
        blendSel.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        blendSel.SetFrequency(EngineConfig.BIOME_BLEND_SELECTOR_FREQ);

        FastNoiseLite jungleSel = new FastNoiseLite(GlobalTerrainUtils.mixSeed(seed, 0xC0FFEE11));
        jungleSel.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        jungleSel.SetFrequency(EngineConfig.BIOME_JUNGLE_SELECTOR_FREQ);


        int i = 0;
        for (int z = 0; z < rect.sizeZ; z++) {
            int wz0 = rect.minZ + z;
            for (int x = 0; x < rect.sizeX; x++) {
                int wx0 = rect.minX + x;

                // Warp sampling coords (keep raw wx0/wz0 for hashing & height lookup)
                FastNoiseLite.Vector2 p = new FastNoiseLite.Vector2(wx0, wz0);
                warp.DomainWarp(p);
                float wx = p.x;
                float wz = p.y;

                // Climate in [0..1]
                float temp  = GlobalTerrainUtils.to01(tempN.GetNoise(wx + 10000.0f, wz - 10000.0f));
                float humid = GlobalTerrainUtils.to01(humidN.GetNoise(wx - 20000.0f, wz + 20000.0f));
                float weird = GlobalTerrainUtils.to01(weirdN.GetNoise(wx + 30000.0f, wz + 30000.0f));

                int h = heightmap.heightAtUnchecked(wx0, wz0);

                // Simple ocean handling for now (later you can add explicit ocean biomes)
                if (h < EngineConfig.SEA_LEVEL - 8) {
                    biomes[i++] = EngineConfig.BIOME_PLAINS;
                    continue;
                }

                // Altitude cooling: higher => colder
                float aboveSea = (h - EngineConfig.SEA_LEVEL) / 128.0f; // ~0..1
                if (aboveSea < 0f) aboveSea = 0f;
                if (aboveSea > 1f) aboveSea = 1f;
                temp = GlobalTerrainUtils.clamp01(temp - aboveSea * EngineConfig.BIOME_ALTITUDE_COOLING);

                // ---- Jungle hard override (coherent patches, only when climate is clearly jungle) ----
                float jungleSuit =
                        GlobalTerrainUtils.smoothstep(EngineConfig.BIOME_JUNGLE_TEMP_MIN,  0.92f, temp) *
                        GlobalTerrainUtils.smoothstep(EngineConfig.BIOME_JUNGLE_HUMID_MIN, 0.92f, humid);

                if (jungleSuit > 0.0f) {
                    float jRnd = GlobalTerrainUtils.to01(jungleSel.GetNoise(wx0, wz0)); // coherent selector
                    // Higher jungleSuit => more likely to become jungle inside suitable area
                    float pJ = jungleSuit * EngineConfig.BIOME_JUNGLE_COVERAGE;

                    // optional: avoid tiny jungle on beaches
                    if (h > EngineConfig.SEA_LEVEL + 2 && jRnd < pJ) {
                        biomes[i++] = EngineConfig.BIOME_JUNGLE;
                        continue;
                    }
                }

                // Pick 2 best biome candidates by distance in climate space
                short b0 = EngineConfig.BIOME_PLAINS;
                short b1 = EngineConfig.BIOME_PLAINS;
                float s0 = Float.POSITIVE_INFINITY;
                float s1 = Float.POSITIVE_INFINITY;

                // plains:  (0.50, 0.45)
                {
                    float sc = score(temp, humid, weird, 0.50f, 0.45f, 0.50f, 0.0f);
                    if (sc < s0) {
                        s1 = s0;
                        b1 = b0;
                        s0 = sc;
                        b0 = EngineConfig.BIOME_PLAINS;
                    } else if (sc < s1) {
                        s1 = sc;  b1 = EngineConfig.BIOME_PLAINS;
                    }
                }

                // forest:  (0.50, 0.70)
                {
                    float sc = score(temp, humid, weird, 0.50f, 0.70f, 0.45f, 0.0f);
                    if (sc < s0) {
                        s1 = s0;
                        b1 = b0;
                        s0 = sc;
                        b0 = EngineConfig.BIOME_FOREST;
                    }
                    else if (sc < s1) {
                        s1 = sc;
                        b1 = EngineConfig.BIOME_FOREST;
                    }
                }

                // desert:  (0.85, 0.20)
                {
                    float sc = score(temp, humid, weird, 0.85f, 0.20f, 0.55f, 0.0f);
                    if (sc < s0) {
                        s1 = s0;
                        b1 = b0;
                        s0 = sc;
                        b0 = EngineConfig.BIOME_DESERT;
                    }
                    else if (sc < s1) {
                        s1 = sc;
                        b1 = EngineConfig.BIOME_DESERT;
                    }
                }

                // savanna: (0.80, 0.45)
                {
                    float sc = score(temp, humid, weird, 0.80f, 0.45f, 0.60f, 0.0f);
                    if (sc < s0) {
                        s1 = s0;
                        b1 = b0;
                        s0 = sc;
                        b0 = EngineConfig.BIOME_SAVANNA;
                    }
                    else if (sc < s1) {
                        s1 = sc;
                        b1 = EngineConfig.BIOME_SAVANNA;
                    }
                }

                // snow:    (0.15, 0.40)
                {
                    float sc = score(temp, humid, weird, 0.15f, 0.40f, 0.45f, 0.0f);
                    if (sc < s0) {
                        s1 = s0;
                        b1 = b0;
                        s0 = sc;
                        b0 = EngineConfig.BIOME_SNOW;
                    }
                    else if (sc < s1) {
                        s1 = sc;
                        b1 = EngineConfig.BIOME_SNOW;
                    }
                }

                // swamp:   (0.55, 0.90) with a bit of weirdness preference around 0.5
                {
                    float sc = score(temp, humid, weird, 0.55f, 0.90f, 0.50f, 0.10f);
                    if (sc < s0) {
                        s1 = s0;
                        b1 = b0;
                        s0 = sc;
                        b0 = EngineConfig.BIOME_SWAMP;
                    }
                    else if (sc < s1) {
                        s1 = sc;
                        b1 = EngineConfig.BIOME_SWAMP;
                    }
                }

                // jungle: hot + very humid (bias helps it win when suitable)
                {
                    float sc = score(temp, humid, weird, 0.78f, 0.82f, 0.50f, 0.0f);
                    sc -= jungleSuit * EngineConfig.BIOME_JUNGLE_BIAS;

                    if (sc < s0) {
                        s1 = s0; b1 = b0;
                        s0 = sc; b0 = EngineConfig.BIOME_JUNGLE;
                    } else if (sc < s1) {
                        s1 = sc; b1 = EngineConfig.BIOME_JUNGLE;
                    }
                }

                // If both candidates ended up identical, no blending needed
                if (b1 == b0) {
                    biomes[i++] = b0;
                    continue;
                }

                // Base probability from score weights
                float w0 = GlobalTerrainUtils.fastExpNeg(EngineConfig.BIOME_BLEND_SHARPNESS * s0);
                float w1 = GlobalTerrainUtils.fastExpNeg(EngineConfig.BIOME_BLEND_SHARPNESS * s1);
                float t = w0 / (w0 + w1); // 0..1 probability of choosing b0 in the ambiguous zone

                // GATE blending by margin: only blend when s1 is close to s0
                float margin = s1 - s0; // >= 0
                float gate = 1.0f - GlobalTerrainUtils.smoothstep(
                    EngineConfig.BIOME_BLEND_MARGIN_START,
                    EngineConfig.BIOME_BLEND_MARGIN_END,
                    margin
                );

                float p0 = 1.0f - gate + gate * t;

                // Use low-frequency selector noise for coherent transition patches
                float rnd = GlobalTerrainUtils.to01(blendSel.GetNoise(wx0, wz0));

                biomes[i++] = (rnd < p0) ? b0 : b1;

            }
        }

        return new BiomeMap(rect, biomes);
    }

    // Distance in (temp, humid) plus optional weirdness term
    private static float score(float t, float h, float w, float ct, float ch, float cw, float weirdWeight) {
        float dt = t - ct;
        float dh = h - ch;
        float dw = (w - cw);
        return dt * dt + dh * dh + (weirdWeight * (dw * dw));
    }
}