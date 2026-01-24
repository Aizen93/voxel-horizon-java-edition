package org.aouessar.core.gen.impl;

import org.aouessar.core.gen.SurfaceDecorator;
import org.aouessar.core.math.GlobalTerrainUtils;
import org.aouessar.core.noise.FastNoiseLite;
import org.aouessar.core.world.Blocks;
import org.aouessar.core.world.layers.BiomeMap;
import org.aouessar.core.world.layers.Heightmap;
import org.aouessar.core.world.layers.LayerRect;
import org.aouessar.core.world.layers.SurfaceRules;
import org.aouessar.shared.EngineConfig;

public final class BiomeDecorator implements SurfaceDecorator {

    @Override
    public SurfaceRules generateSurfaceRules(Heightmap heightmap, BiomeMap biomeMap) {
        LayerRect rect = heightmap.rect();
        int n = rect.sizeX * rect.sizeZ;

        short[] top = new short[n];
        short[] filler = new short[n];
        byte[] depth = new byte[n];

        // Coherent patch noise for ecotones (NOT salt-and-pepper)
        FastNoiseLite eco = new FastNoiseLite(905282311);
        eco.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        eco.SetFrequency(1.0f / 96.0f); // tune: smaller => larger patches

        int i = 0;
        for (int z = 0; z < rect.sizeZ; z++) {
            int wz = rect.minZ + z;
            for (int x = 0; x < rect.sizeX; x++) {
                int wx = rect.minX + x;

                int h = heightmap.heightAtUnchecked(wx, wz);

                // Base biome id
                short b = biomeMap.biomeIdAtUnchecked(wx, wz);

                // Your existing border nudge (keeps biome borders less jagged)
                short majority = majorityBiome4(biomeMap, rect, wx, wz, b);
                if (majority != b) {
                    float r = GlobalTerrainUtils.hash01(905282311L, wx, wz);
                    b = (r < 0.70f) ? b : majority;
                }

                // ---- Beaches / underwater ----
                // (ChunkBuilder will still do stronger shoreline rules; this keeps SurfaceRules sane.)
                if (h <= EngineConfig.SEA_LEVEL - 1) {
                    top[i] = Blocks.SAND;
                    filler[i] = Blocks.SANDSTONE; // nice: sand->sandstone below even underwater
                    depth[i] = 4;
                    i++;
                    continue;
                }
                if (h <= EngineConfig.SEA_LEVEL + EngineConfig.BIOME_BEACH_BAND) {
                    top[i] = Blocks.SAND;
                    filler[i] = Blocks.SAND;
                    depth[i] = 4;
                    i++;
                    continue;
                }

                // ---- Base palette for biome ----
                Palette self = paletteFor(b, h);

                short topBlock = self.top;
                short fillerBlock = self.filler;
                int fillDepth = self.depth;

                // ---- Generic ecotone blending near borders ----
                // Find a dominant different neighbor (radius 2 gives smoother/ecotone width)
                short otherBiome = dominantDifferentNeighbor(biomeMap, rect, wx, wz, b, 2);
                if (otherBiome != b) {
                    Palette other = paletteFor(otherBiome, h);

                    // how "border-ish" is this column: count how many neighbors differ
                    int diff = countDifferentNeighbors(biomeMap, rect, wx, wz, b, 2);
                    float edge = GlobalTerrainUtils.clamp01(diff / 12.0f); // r=2 => normalize

                    float n01 = (eco.GetNoise(wx, wz) + 1f) * 0.5f; // 0..1

                    // Per-biome dominance: deserts and snow stay more “pure” deeper inside
                    float selfBias = dominanceBias(b);

                    // Edge pushes toward mixing; deeper inside keeps self palette
                    float threshold = GlobalTerrainUtils.clamp01(selfBias + (edge - 0.5f) * 0.85f);

                    // Special-case snow transitions so snow feels altitude-driven and patchy
                    // (prevents "hard white paint")
                    if (b == EngineConfig.BIOME_SNOW || otherBiome == EngineConfig.BIOME_SNOW) {
                        float snowFactor = snowlineFactor(h); // 0..1
                        // If current biome is snow but altitude is low -> reduce snow dominance
                        if (b == EngineConfig.BIOME_SNOW) threshold = GlobalTerrainUtils.clamp01(threshold * snowFactor);
                        // If neighbor is snow and altitude supports it -> allow some intrusion
                        if (otherBiome == EngineConfig.BIOME_SNOW) threshold = GlobalTerrainUtils.clamp01(threshold + snowFactor * 0.15f);
                    }

                    boolean keepSelf = (n01 < threshold);
                    Palette chosen = keepSelf ? self : other;

                    topBlock = chosen.top;
                    fillerBlock = chosen.filler;
                    fillDepth = Math.max(fillDepth, chosen.depth);
                }

                top[i] = topBlock;
                filler[i] = fillerBlock;
                depth[i] = (byte) fillDepth;
                i++;
            }
        }

        return new SurfaceRules(rect, top, filler, depth);
    }

    // ---------------- helpers ----------------

    private record Palette(short top, short filler, int depth) {}

    private static Palette paletteFor(short biome, int heightY) {
        // Desert: red sand + red sandstone foundation
        if (biome == EngineConfig.BIOME_DESERT) {
            return new Palette(Blocks.DESERT_SAND, Blocks.DESERT_SAND, 6);
        }

        // Savanna: dry grass
        if (biome == EngineConfig.BIOME_SAVANNA) {
            return new Palette(Blocks.DRY_GRASS, Blocks.DIRT, 4);
        }

        // Forest: regular green grass (like Minecraft oak/birch forests)
        if (biome == EngineConfig.BIOME_FOREST) {
            return new Palette(Blocks.GRASS, Blocks.DIRT, 4);
        }

        // Jungle: podzol gives that dense tropical floor debris look
        if (biome == EngineConfig.BIOME_JUNGLE) {
            return new Palette(Blocks.PODZOl_DIRT, Blocks.DIRT, 4);
        }

        // Swamp: regular grass (could add mud block if available)
        if (biome == EngineConfig.BIOME_SWAMP) {
            return new Palette(Blocks.GRASS, Blocks.DIRT, 5);
        }

        // Snow: use SNOW_GRASS at moderate altitudes, SNOW at higher altitudes
        if (biome == EngineConfig.BIOME_SNOW) {
            // altitude threshold: tweak if needed
            int aboveSea = heightY - EngineConfig.SEA_LEVEL;
            short top = (aboveSea >= 28) ? Blocks.SNOW : Blocks.SNOW_GRASS;
            return new Palette(top, Blocks.DIRT, 4);
        }

        // Plains: default green grass
        return new Palette(Blocks.GRASS, Blocks.DIRT, 4);
    }

    private static float dominanceBias(short biome) {
        // Higher => keeps its own palette more strongly near borders
        if (biome == EngineConfig.BIOME_DESERT) return 0.82f;
        if (biome == EngineConfig.BIOME_SNOW)   return 0.78f;
        if (biome == EngineConfig.BIOME_FOREST) return 0.70f;
        return 0.62f;
    }

    // Smooth altitude-driven snowline factor (0..1)
    private static float snowlineFactor(int h) {
        int aboveSea = h - EngineConfig.SEA_LEVEL;
        // Start allowing snow around +10, fully snow around +45 (tweakable)
        float t = (aboveSea - 10) / 35.0f;
        if (t < 0f) t = 0f;
        if (t > 1f) t = 1f;
        return t * t * (3f - 2f * t);
    }

    private short dominantDifferentNeighbor(BiomeMap map, LayerRect rect, int wx, int wz, short self, int r) {
        int cDes = 0;
        int cSnow = 0;
        int cFor = 0;
        int cSav = 0;
        int cPla = 0;
        int cSwp = 0;
        int cJun = 0;

        for (int dz = -r; dz <= r; dz++) {
            for (int dx = -r; dx <= r; dx++) {
                if (dx == 0 && dz == 0) continue;
                short b = sample(map, rect, wx + dx, wz + dz, self);
                if (b == self) continue;

                switch (b) {
                    case EngineConfig.BIOME_DESERT -> cDes++;
                    case EngineConfig.BIOME_SNOW -> cSnow++;
                    case EngineConfig.BIOME_FOREST -> cFor++;
                    case EngineConfig.BIOME_SAVANNA -> cSav++;
                    case EngineConfig.BIOME_SWAMP -> cSwp++;
                    case EngineConfig.BIOME_JUNGLE -> cJun++;
                    default -> cPla++; // default bucket
                }
            }
        }

        short best = self;
        int bestC = 0;

        if (cDes > bestC) { bestC = cDes; best = EngineConfig.BIOME_DESERT; }
        if (cSnow > bestC) { bestC = cSnow; best = EngineConfig.BIOME_SNOW; }
        if (cFor > bestC) { bestC = cFor; best = EngineConfig.BIOME_FOREST; }
        if (cSav > bestC) { bestC = cSav; best = EngineConfig.BIOME_SAVANNA; }
        if (cSwp > bestC) { bestC = cSwp; best = EngineConfig.BIOME_SWAMP; }
        if (cPla > bestC) { bestC = cPla; best = EngineConfig.BIOME_PLAINS; }
        if (cJun > bestC) { best = EngineConfig.BIOME_JUNGLE; }

        return best;
    }

    private int countDifferentNeighbors(BiomeMap map, LayerRect rect, int wx, int wz, short self, int r) {
        int diff = 0;
        for (int dz = -r; dz <= r; dz++) {
            for (int dx = -r; dx <= r; dx++) {
                if (dx == 0 && dz == 0) continue;
                short b = sample(map, rect, wx + dx, wz + dz, self);
                if (b != self) diff++;
            }
        }
        return diff;
    }

    private short sample(BiomeMap map, LayerRect rect, int x, int z, short fallback) {
        if (x < rect.minX || x >= rect.minX + rect.sizeX) return fallback;
        if (z < rect.minZ || z >= rect.minZ + rect.sizeZ) return fallback;
        return map.biomeIdAtUnchecked(x, z);
    }

    // ---------------- helpers (inside BiomeDecorator) ----------------

    private short majorityBiome4(BiomeMap map, LayerRect rect, int wx, int wz, short self) {
        // Sample neighbors safely within rect (avoid out-of-rect checks if you want strict only)
        short a = sample(map, rect, wx - 1, wz, self);
        short b = sample(map, rect, wx + 1, wz, self);
        short c = sample(map, rect, wx, wz - 1, self);
        short d = sample(map, rect, wx, wz + 1, self);

        // Simple plurality vote among {self,a,b,c,d}
        short best = self;
        int bestCount = GlobalTerrainUtils.count(self, self, a, b, c, d);

        short[] candidates = new short[]{a, b, c, d};
        for (short cand : candidates) {
            int ct = GlobalTerrainUtils.count(cand, self, a, b, c, d);
            if (ct > bestCount) { bestCount = ct; best = cand; }
        }
        return best;
    }
}