package org.aouessar.shared;

public final class EngineConfig {
    private EngineConfig() {}

    // -----------------------------
    // World geometry (Minecraft-style)
    // -----------------------------
    public static final int CHUNK_SIZE = 16;

    public static final int REGION_SIZE_CHUNKS = 16;
    public static final int REGION_SIZE_BLOCKS = REGION_SIZE_CHUNKS * CHUNK_SIZE;

    // Minecraft 1.18+ overworld vertical range: -64..319 (inclusive) :contentReference[oaicite:1]{index=1}
    public static final int MIN_Y = -64;
    public static final int MAX_Y = 319;
    public static final int WORLD_HEIGHT = (MAX_Y - MIN_Y + 1); // 384
    public static final float WATER_TOP_DELTA = 0.2f;

    // Vanilla-ish default sea level used by many tools/resources is 62 :contentReference[oaicite:2]{index=2}
    public static final int SEA_LEVEL = 62;

    // -----------------------------
    // Threading (core + renderer)
    // -----------------------------
    public static final int CPU_WORKERS = Math.max(2, Runtime.getRuntime().availableProcessors() - 1);

    // ======================= TERRAIN TUNING =======================
// All values are in "blocks" or "1/blocks" (frequency).
// Changing these changes world shape but remains deterministic (seed+coord).

    // World scale (bigger denominator => bigger features)
    public static final float TERRAIN_CONTINENT_FREQ = 1.0f / 8192.0f;
    public static final float TERRAIN_LARGE_FREQ     = 1.0f / 2048.0f;
    public static final float TERRAIN_RIDGE_FREQ     = 1.0f / 1536.0f;
    public static final float TERRAIN_DETAIL_FREQ    = 1.0f / 256.0f;

    // Domain warp
    public static final float TERRAIN_WARP_FREQ      = 1.0f / 4096.0f;
    public static final float TERRAIN_WARP_AMP_BLOCKS = 180.0f;

    // Coastline / landmass shape
// land = smoothstep(COAST_OCEAN, COAST_LAND, continentalness)
    public static final float TERRAIN_COAST_OCEAN = -0.25f;
    public static final float TERRAIN_COAST_LAND  =  0.10f;

    // inland = smoothstep(INLAND_START, INLAND_FULL, continentalness)
    public static final float TERRAIN_INLAND_START = 0.10f;
    public static final float TERRAIN_INLAND_FULL  = 0.70f;

    // Elevation (blocks)
    public static final float TERRAIN_BASE_LAND_UPLIFT   = 54.0f;
    public static final float TERRAIN_LARGE_AMPLITUDE    = 22.0f;
    public static final float TERRAIN_HILL_AMPLITUDE     = 10.0f;
    public static final float TERRAIN_MOUNTAIN_AMPLITUDE = 110.0f;

    // Ocean depth (blocks)
    public static final float TERRAIN_OCEAN_BASE_DEPTH  = 18.0f;
    public static final float TERRAIN_OCEAN_EXTRA_DEPTH = 36.0f;
    public static final float TERRAIN_SEABED_VARIATION  = 6.0f;
    public static final float TERRAIN_OCEAN_LARGE_VARIATION = 8.0f;

    // Mountains shaping
    public static final float TERRAIN_RIDGE_MIN = 0.15f;
    public static final float TERRAIN_RIDGE_MAX = 0.65f;
    public static final float TERRAIN_EROSION_MIN = 0.20f;
    public static final float TERRAIN_EROSION_MAX = 0.80f;
    public static final float TERRAIN_RIDGE_EXP = 1.55f;

    // ===================== END TERRAIN TUNING =====================




    // ======================= BIOME TUNING =======================

    // Climate noise frequencies (smaller freq => larger biomes)
    public static final float BIOME_TEMP_FREQ   = 1.0f / 4096.0f;
    public static final float BIOME_HUMID_FREQ  = 1.0f / 4096.0f;
    public static final float BIOME_WEIRD_FREQ  = 1.0f / 2048.0f;

    // Domain warp to make biome borders less “noise-field-y”
    public static final float BIOME_WARP_FREQ       = 1.0f / 3072.0f;
    public static final float BIOME_WARP_AMP_BLOCKS = 220.0f;

    // How much altitude cools temperature (0.0..1.0 range). Higher => more snow on mountains.
    public static final float BIOME_ALTITUDE_COOLING = 0.35f;

    // Soft blending strength: higher => sharper borders, lower => wider transition zones.
    // Typical range: 2..8
    public static final float BIOME_BLEND_SHARPNESS = 4.0f;

    // Sea/shore blending: how many blocks above sea level count as “beach band”.
    public static final int BIOME_BEACH_BAND = 3;

    // Biome IDs (keep or change as you like, but generator/decorator should match)
    public static final short BIOME_PLAINS = 0;
    public static final short BIOME_DESERT = 1;
    public static final short BIOME_SNOW   = 2;
    public static final short BIOME_FOREST = 3;
    public static final short BIOME_SAVANNA = 4;
    public static final short BIOME_SWAMP  = 5;
    public static final short BIOME_JUNGLE = 6;

    // ===================== END BIOME TUNING =====================



    // ======================= CHUNK COMPOSITION (minecraft-like) =======================

    // Bedrock thickness at world bottom (fixed because ChunkBuilder doesn't receive seed)
    public static final int BEDROCK_THICKNESS = 2;

    // Deepslate begins below this Y (classic-ish feel)
    public static final int DEEPSLATE_START_Y = 16;

    // Beach band around sea level (used if you already added BIOME_BEACH_BAND, keep one)
    public static final int BEACH_BAND = 3;

    // Ocean floor top material mix (simple ratios)
    public static final int OCEAN_CLAY_CHANCE_PER_256 = 40;    // ~15%
    public static final int OCEAN_GRAVEL_CHANCE_PER_256 = 90;  // ~35%

    // River carving depth and riverbed thickness
    public static final int RIVER_CARVE_DEPTH = 3;
    public static final int RIVERBED_THICKNESS = 2;

    // ===============================================================================

    // ======================= BIOME BLEND GATING =======================
    // Scores are squared distances, so margins are small numbers (~0..0.2 typically).
    // If mixing is too wide -> LOWER START/END? (actually: lower => less blending)
    // Practical tuning:
    // - Start ~0.020
    // - End   ~0.060
    public static final float BIOME_BLEND_MARGIN_START = 0.020f;
    public static final float BIOME_BLEND_MARGIN_END   = 0.060f;

    // Selector noise frequency (bigger denominator => larger patches)
    public static final float BIOME_BLEND_SELECTOR_FREQ = 1.0f / 192.0f;
    // ===================== END BIOME BLEND GATING =====================

    public static final float BIOME_JUNGLE_TEMP_MIN  = 0.72f;
    public static final float BIOME_JUNGLE_HUMID_MIN = 0.72f;
    public static final float BIOME_JUNGLE_BIAS = 0.05f;
    // Jungle patch selector (coherent blobs)
    public static final float BIOME_JUNGLE_SELECTOR_FREQ = 1.0f / 768.0f;
    // How much of "suitable" area becomes jungle (0..1)
    public static final float BIOME_JUNGLE_COVERAGE = 0.65f;

}
