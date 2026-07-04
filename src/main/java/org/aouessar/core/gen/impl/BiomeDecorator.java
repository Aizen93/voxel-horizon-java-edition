package org.aouessar.core.gen.impl;

import org.aouessar.core.gen.SurfaceDecorator;
import org.aouessar.core.gen.config.BiomeConfig;
import org.aouessar.core.gen.config.WorldContentConfig;
import org.aouessar.core.gen.config.WorldContentLoader;
import org.aouessar.core.math.GlobalTerrainUtils;
import org.aouessar.core.noise.FastNoiseLite;
import org.aouessar.core.world.Blocks;
import org.aouessar.core.world.layers.BiomeMap;
import org.aouessar.core.world.layers.Heightmap;
import org.aouessar.core.world.layers.LayerRect;
import org.aouessar.core.world.layers.SurfaceRules;
import org.aouessar.shared.EngineConfig;

public final class BiomeDecorator implements SurfaceDecorator {

    // Static palette data loaded once from JSON at class initialization
    private static final short[] BIOME_TOP_BLOCKS = new short[16];
    private static final short[] BIOME_FILLER_BLOCKS = new short[16];
    private static final int[] BIOME_FILLER_DEPTHS = new int[16];

    static {
        // Set defaults first
        for (int i = 0; i < 16; i++) {
            BIOME_TOP_BLOCKS[i] = Blocks.GRASS;
            BIOME_FILLER_BLOCKS[i] = Blocks.DIRT;
            BIOME_FILLER_DEPTHS[i] = 4;
        }

        // Set defaults for ocean biomes (not in JSON config)
        BIOME_TOP_BLOCKS[EngineConfig.BIOME_OCEAN] = Blocks.SAND;
        BIOME_FILLER_BLOCKS[EngineConfig.BIOME_OCEAN] = Blocks.SAND;
        BIOME_FILLER_DEPTHS[EngineConfig.BIOME_OCEAN] = 4;

        BIOME_TOP_BLOCKS[EngineConfig.BIOME_DEEP_OCEAN] = Blocks.GRAVEL;
        BIOME_FILLER_BLOCKS[EngineConfig.BIOME_DEEP_OCEAN] = Blocks.GRAVEL;
        BIOME_FILLER_DEPTHS[EngineConfig.BIOME_DEEP_OCEAN] = 3;

        // Load from JSON config (climate biomes)
        try {
            WorldContentConfig config = WorldContentLoader.load();
            loadPalette(config, EngineConfig.BIOME_PLAINS, "PLAINS");
            loadPalette(config, EngineConfig.BIOME_DESERT, "DESERT");
            loadPalette(config, EngineConfig.BIOME_SNOW, "SNOW");
            loadPalette(config, EngineConfig.BIOME_FOREST, "FOREST");
            loadPalette(config, EngineConfig.BIOME_SAVANNA, "SAVANNA");
            loadPalette(config, EngineConfig.BIOME_SWAMP, "SWAMP");
            loadPalette(config, EngineConfig.BIOME_JUNGLE, "JUNGLE");
        } catch (Exception e) {
            System.err.println("Failed to load world content config, using defaults: " + e.getMessage());
        }
    }

    private static void loadPalette(WorldContentConfig config, short biomeId, String biomeName) {
        BiomeConfig bc = config.getBiome(biomeName);
        if (bc != null) {
            BIOME_TOP_BLOCKS[biomeId] = bc.topBlock();
            BIOME_FILLER_BLOCKS[biomeId] = bc.fillerBlock();
            BIOME_FILLER_DEPTHS[biomeId] = bc.fillerDepth();
        }
    }

    @Override
    public SurfaceRules generateSurfaceRules(long seed, Heightmap heightmap, BiomeMap biomeMap) {
        LayerRect rect = heightmap.rect();
        int n = rect.sizeX * rect.sizeZ;

        short[] top = new short[n];
        short[] filler = new short[n];
        byte[] depth = new byte[n];

        // Coherent patch noise for ecotones (NOT salt-and-pepper).
        // Seeded from the world seed so every world gets its own ecotone pattern.
        FastNoiseLite eco = new FastNoiseLite(GlobalTerrainUtils.mixSeed(seed, 0x0EC07031));
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

                // ---- Geographic biomes: use their fixed palettes, skip climate blending ----
                if (isGeographicBiome(b)) {
                    top[i] = BIOME_TOP_BLOCKS[b];
                    filler[i] = BIOME_FILLER_BLOCKS[b];
                    depth[i] = (byte) BIOME_FILLER_DEPTHS[b];
                    i++;
                    continue;
                }

                // Your existing border nudge (keeps biome borders less jagged)
                short majority = majorityBiome4(biomeMap, rect, wx, wz, b);
                if (majority != b && !isGeographicBiome(majority)) {
                    float r = GlobalTerrainUtils.hash01(seed, wx, wz);
                    b = (r < 0.70f) ? b : majority;
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

    /**
     * Default top block for a biome at a given surface height (world Y),
     * without ecotone blending. Shared with the far-field LOD sampler so
     * distant terrain uses the same palette as near-field surface rules.
     */
    public static short defaultTopBlock(short biomeId, int heightY) {
        if (biomeId < 0 || biomeId >= BIOME_TOP_BLOCKS.length) return Blocks.GRASS;
        if (biomeId == EngineConfig.BIOME_SNOW) {
            int aboveSea = heightY - EngineConfig.SEA_LEVEL;
            return (aboveSea >= 28) ? Blocks.SNOW : BIOME_TOP_BLOCKS[biomeId];
        }
        return BIOME_TOP_BLOCKS[biomeId];
    }

    /**
     * Check if the biome is an ocean biome (geographic, not climate-driven).
     */
    private static boolean isGeographicBiome(short biome) {
        return biome == EngineConfig.BIOME_OCEAN
            || biome == EngineConfig.BIOME_DEEP_OCEAN;
    }

    private record Palette(short top, short filler, int depth) {}

    private static Palette paletteFor(short biome, int heightY) {
        // Geographic biomes: use their fixed palettes
        if (isGeographicBiome(biome)) {
            return new Palette(BIOME_TOP_BLOCKS[biome], BIOME_FILLER_BLOCKS[biome], BIOME_FILLER_DEPTHS[biome]);
        }

        // Snow: use SNOW_GRASS at moderate altitudes, SNOW at higher altitudes
        if (biome == EngineConfig.BIOME_SNOW) {
            int aboveSea = heightY - EngineConfig.SEA_LEVEL;
            short topBlock = (aboveSea >= 28) ? Blocks.SNOW : BIOME_TOP_BLOCKS[biome];
            return new Palette(topBlock, BIOME_FILLER_BLOCKS[biome], BIOME_FILLER_DEPTHS[biome]);
        }

        // All other biomes: use JSON-loaded data
        return new Palette(BIOME_TOP_BLOCKS[biome], BIOME_FILLER_BLOCKS[biome], BIOME_FILLER_DEPTHS[biome]);
    }

    private static float dominanceBias(short biome) {
        // Higher => keeps its own palette more strongly near borders
        // Ocean biomes have high dominance to preserve their appearance
        if (biome == EngineConfig.BIOME_OCEAN) return 0.95f;
        if (biome == EngineConfig.BIOME_DEEP_OCEAN) return 0.95f;
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
        // Count climate biomes
        int cDes = 0;
        int cSnow = 0;
        int cFor = 0;
        int cSav = 0;
        int cPla = 0;
        int cSwp = 0;
        int cJun = 0;
        // Count ocean biomes
        int cOcean = 0;
        int cDeepOcean = 0;

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
                    case EngineConfig.BIOME_OCEAN -> cOcean++;
                    case EngineConfig.BIOME_DEEP_OCEAN -> cDeepOcean++;
                    default -> cPla++; // default bucket
                }
            }
        }

        short best = self;
        int bestC = 0;

        // Climate biomes
        if (cDes > bestC) { bestC = cDes; best = EngineConfig.BIOME_DESERT; }
        if (cSnow > bestC) { bestC = cSnow; best = EngineConfig.BIOME_SNOW; }
        if (cFor > bestC) { bestC = cFor; best = EngineConfig.BIOME_FOREST; }
        if (cSav > bestC) { bestC = cSav; best = EngineConfig.BIOME_SAVANNA; }
        if (cSwp > bestC) { bestC = cSwp; best = EngineConfig.BIOME_SWAMP; }
        if (cPla > bestC) { bestC = cPla; best = EngineConfig.BIOME_PLAINS; }
        if (cJun > bestC) { bestC = cJun; best = EngineConfig.BIOME_JUNGLE; }
        // Ocean biomes
        if (cOcean > bestC) { bestC = cOcean; best = EngineConfig.BIOME_OCEAN; }
        if (cDeepOcean > bestC) { best = EngineConfig.BIOME_DEEP_OCEAN; }

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