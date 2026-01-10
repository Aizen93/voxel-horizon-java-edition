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

        // ---- Warp (keeps borders organic) ----
        FastNoiseLite warp = new FastNoiseLite(GlobalTerrainUtils.mixSeed(seed, 0x6A09E667));
        warp.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        warp.SetFrequency(EngineConfig.BIOME_WARP_FREQ);
        warp.SetFractalType(FastNoiseLite.FractalType.DomainWarpProgressive);
        warp.SetFractalOctaves(2);
        warp.SetFractalGain(0.5f);
        warp.SetFractalLacunarity(2.0f);
        warp.SetDomainWarpAmp(EngineConfig.BIOME_WARP_AMP_BLOCKS);

        // ---- Fine climate (your existing) ----
        FastNoiseLite tempFineN = new FastNoiseLite(GlobalTerrainUtils.mixSeed(seed, 0xBB67AE85));
        tempFineN.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        tempFineN.SetFrequency(EngineConfig.BIOME_TEMP_FREQ);
        tempFineN.SetFractalType(FastNoiseLite.FractalType.FBm);
        tempFineN.SetFractalOctaves(4);
        tempFineN.SetFractalGain(0.5f);
        tempFineN.SetFractalLacunarity(2.0f);

        FastNoiseLite humidFineN = new FastNoiseLite(GlobalTerrainUtils.mixSeed(seed, 0x3C6EF372));
        humidFineN.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        humidFineN.SetFrequency(EngineConfig.BIOME_HUMID_FREQ);
        humidFineN.SetFractalType(FastNoiseLite.FractalType.FBm);
        humidFineN.SetFractalOctaves(4);
        humidFineN.SetFractalGain(0.5f);
        humidFineN.SetFractalLacunarity(2.0f);

        FastNoiseLite weirdN = new FastNoiseLite(GlobalTerrainUtils.mixSeed(seed, 0xA54FF53A));
        weirdN.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        weirdN.SetFrequency(EngineConfig.BIOME_WEIRD_FREQ);
        weirdN.SetFractalType(FastNoiseLite.FractalType.FBm);
        weirdN.SetFractalOctaves(3);
        weirdN.SetFractalGain(0.5f);
        weirdN.SetFractalLacunarity(2.0f);

        // ---- Macro climate (drives LARGE biomes) ----
        FastNoiseLite tempMacroN = new FastNoiseLite(GlobalTerrainUtils.mixSeed(seed, 0xBB67AE86));
        tempMacroN.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        tempMacroN.SetFrequency(EngineConfig.BIOME_TEMP_FREQ * EngineConfig.BIOME_MACRO_FREQ_MULT);
        tempMacroN.SetFractalType(FastNoiseLite.FractalType.FBm);
        tempMacroN.SetFractalOctaves(2);
        tempMacroN.SetFractalGain(0.5f);
        tempMacroN.SetFractalLacunarity(2.0f);

        FastNoiseLite humidMacroN = new FastNoiseLite(GlobalTerrainUtils.mixSeed(seed, 0x3C6EF373));
        humidMacroN.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        humidMacroN.SetFrequency(EngineConfig.BIOME_HUMID_FREQ * EngineConfig.BIOME_MACRO_FREQ_MULT);
        humidMacroN.SetFractalType(FastNoiseLite.FractalType.FBm);
        humidMacroN.SetFractalOctaves(2);
        humidMacroN.SetFractalGain(0.5f);
        humidMacroN.SetFractalLacunarity(2.0f);

        // ---- Boundary wiggle (prevents straight-line transitions) ----
        FastNoiseLite zoneSel = new FastNoiseLite(GlobalTerrainUtils.mixSeed(seed, 0xD00DFEED));
        zoneSel.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        zoneSel.SetFrequency(EngineConfig.BIOME_ZONE_SEL_FREQ);

        // ---- Coherent jungle selector (optional patchiness inside hot+wet) ----
        FastNoiseLite jungleSel = new FastNoiseLite(GlobalTerrainUtils.mixSeed(seed, 0xC0FFEE11));
        jungleSel.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        jungleSel.SetFrequency(EngineConfig.BIOME_JUNGLE_SELECTOR_FREQ);

        final int sea = EngineConfig.SEA_LEVEL;
        final float macroMix = EngineConfig.BIOME_MACRO_MIX;

        int i = 0;
        for (int z = 0; z < rect.sizeZ; z++) {
            int wz0 = rect.minZ + z;
            for (int x = 0; x < rect.sizeX; x++) {
                int wx0 = rect.minX + x;

                // Warp sampling coords (use consistently for ALL climate)
                FastNoiseLite.Vector2 p = new FastNoiseLite.Vector2(wx0, wz0);
                warp.DomainWarp(p);
                float wx = p.x;
                float wz = p.y;

                // Mild coord rotation to avoid axis-aligned artifacts
                float tx = 0.8660254f * wx + 0.5f * wz;
                float tz = -0.5f * wx + 0.8660254f * wz;

                float hx = 0.7071068f * wx - 0.7071068f * wz;
                float hz = 0.7071068f * wx + 0.7071068f * wz;

                // Fine climate
                float tempFine  = GlobalTerrainUtils.to01(tempFineN.GetNoise(tx + 10000.0f, tz - 10000.0f));
                float humidFine = GlobalTerrainUtils.to01(humidFineN.GetNoise(hx - 20000.0f, hz + 20000.0f));

                // Macro climate
                float tempMacro  = GlobalTerrainUtils.to01(tempMacroN.GetNoise(tx - 7000.0f, tz + 7000.0f));
                float humidMacro = GlobalTerrainUtils.to01(humidMacroN.GetNoise(hx + 9000.0f, hz - 9000.0f));

                // Mix: macro dominates => big biomes, fine adds gentle variation
                float temp  = GlobalTerrainUtils.clamp01(org.joml.Math.lerp(tempFine,  tempMacro,  macroMix));
                float humid = GlobalTerrainUtils.clamp01(org.joml.Math.lerp(humidFine, humidMacro, macroMix));

                float weird = GlobalTerrainUtils.to01(weirdN.GetNoise(wx + 30000.0f, wz + 30000.0f));

                int h = heightmap.heightAtUnchecked(wx0, wz0);

                // Ocean handling (keep as-is for now)
                if (h < sea - 8) {
                    biomes[i++] = EngineConfig.BIOME_PLAINS;
                    continue;
                }

                // Altitude cooling (unchanged)
                float aboveSea = (h - sea) / 128.0f;
                if (aboveSea < 0f) aboveSea = 0f;
                if (aboveSea > 1f) aboveSea = 1f;
                temp = GlobalTerrainUtils.clamp01(temp - aboveSea * EngineConfig.BIOME_ALTITUDE_COOLING);

                // Threshold wiggle so borders aren’t straight
                float sel01 = GlobalTerrainUtils.to01(zoneSel.GetNoise(wx, wz));
                float wig = (sel01 - 0.5f) * (2.0f * EngineConfig.BIOME_ZONE_WIGGLE);

                float coldMax = EngineConfig.BIOME_TEMP_COLD_MAX + wig;
                float hotMin  = EngineConfig.BIOME_TEMP_HOT_MIN  + wig;

                // -------- Biome decision tree (prevents illogical adjacency) --------
                short biome;

                if (temp <= coldMax) {
                    // Cold zone: only cold biomes
                    biome = EngineConfig.BIOME_SNOW;
                }
                else if (temp >= hotMin) {
                    // Hot zone: desert/savanna/jungle only
                    if (humid <= EngineConfig.BIOME_HUMID_DESERT_MAX) {
                        biome = EngineConfig.BIOME_DESERT;
                    } else {
                        // Jungle only when clearly hot+very humid (coherent patches)
                        float jungleSuit =
                                GlobalTerrainUtils.smoothstep(EngineConfig.BIOME_JUNGLE_TEMP_MIN,  0.92f, temp) *
                                        GlobalTerrainUtils.smoothstep(EngineConfig.BIOME_JUNGLE_HUMID_MIN, 0.92f, humid);

                        if (jungleSuit > 0.0f) {
                            float jRnd = GlobalTerrainUtils.to01(jungleSel.GetNoise(wx, wz));
                            float pJ = jungleSuit * EngineConfig.BIOME_JUNGLE_COVERAGE;
                            if (h > sea + 2 && jRnd < pJ && humid >= EngineConfig.BIOME_HUMID_JUNGLE_MIN) {
                                biome = EngineConfig.BIOME_JUNGLE;
                            } else {
                                biome = EngineConfig.BIOME_SAVANNA;
                            }
                        } else {
                            biome = EngineConfig.BIOME_SAVANNA;
                        }
                    }
                }
                else {
                    // Temperate zone: plains/forest/swamp
                    if (humid >= EngineConfig.BIOME_HUMID_SWAMP_MIN && Math.abs(weird - 0.5f) < 0.20f) {
                        biome = EngineConfig.BIOME_SWAMP;
                    } else if (humid >= EngineConfig.BIOME_HUMID_FOREST_MIN) {
                        biome = EngineConfig.BIOME_FOREST;
                    } else {
                        biome = EngineConfig.BIOME_PLAINS;
                    }
                }

                biomes[i++] = biome;
            }
        }

        return new BiomeMap(rect, biomes);
    }
}