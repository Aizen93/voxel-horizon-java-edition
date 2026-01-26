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

    // Domain warp - lower amplitude = smoother coastlines, fewer tiny islands
    public static final float TERRAIN_WARP_FREQ      = 1.0f / 4096.0f;
    public static final float TERRAIN_WARP_AMP_BLOCKS = 120.0f; // was 180.0f - reduced for smoother coasts

    // Coastline / landmass shape
    // land = smoothstep(COAST_OCEAN, COAST_LAND, continentalness)
    // Wider gap = smoother coastlines, narrower gap = sharper coastlines
    // Higher values = fewer small islands, more continuous land
    public static final float TERRAIN_COAST_OCEAN = -0.05f;  // was -0.15f - raised significantly to eliminate small islands
    public static final float TERRAIN_COAST_LAND  =  0.30f;  // was 0.20f - raised for much cleaner land boundaries

    // inland = smoothstep(INLAND_START, INLAND_FULL, continentalness)
    public static final float TERRAIN_INLAND_START = 0.30f;  // was 0.20f
    public static final float TERRAIN_INLAND_FULL  = 0.70f;

    // Elevation (blocks)
    public static final float TERRAIN_BASE_LAND_UPLIFT   = 54.0f;
    public static final float TERRAIN_LARGE_AMPLITUDE    = 22.0f;
    public static final float TERRAIN_HILL_AMPLITUDE     = 10.0f;
    public static final float TERRAIN_MOUNTAIN_AMPLITUDE = 110.0f;

    // Ocean depth (blocks)
    // The ocean floor uses gradual curves, so these values are maximum contributions
    public static final float TERRAIN_OCEAN_BASE_DEPTH  = 8.0f;   // was 12.0f - very shallow near coast
    public static final float TERRAIN_OCEAN_EXTRA_DEPTH = 45.0f;  // was 50.0f - deep ocean contribution
    public static final float TERRAIN_SEABED_VARIATION  = 6.0f;   // was 8.0f - subtle variation
    public static final float TERRAIN_OCEAN_LARGE_VARIATION = 8.0f; // was 12.0f - less chaotic near shore

    // Deep ocean underwater terrain (only applies far from shore)
    public static final float TERRAIN_DEEP_OCEAN_MIN_DEPTH = 45.0f;  // was 35.0f - deeper minimum for true deep ocean
    public static final float TERRAIN_DEEP_OCEAN_EXTRA_DEPTH = 35.0f; // was 40.0f - variation in deep areas
    public static final float TERRAIN_OCEAN_TRENCH_DEPTH = 30.0f;  // was 25.0f - deeper trenches

    // Mountains shaping
    public static final float TERRAIN_RIDGE_MIN = 0.15f;
    public static final float TERRAIN_RIDGE_MAX = 0.65f;
    public static final float TERRAIN_EROSION_MIN = 0.20f;
    public static final float TERRAIN_EROSION_MAX = 0.80f;
    public static final float TERRAIN_RIDGE_EXP = 1.55f;

    // + => more land, - => more ocean
    public static final float TERRAIN_CONTINENT_BIAS = 0.22f; // was 0.18f - more land bias
    // >1 makes extremes more common (bigger solid land + deep ocean), <1 makes more coasts/islands
    public static final float TERRAIN_CONTINENT_CONTRAST = 1.50f; // was 1.25f - much higher to eliminate fragmentation

    // --- Mountain ranges (rare, long chains) (Himalaya-like )---
    public static final float TERRAIN_RANGE_FREQ = 1.0f / 8192.0f;   // big features
    public static final float TERRAIN_RANGE_MIN  = 0.60f;            // higher => rarer
    public static final float TERRAIN_RANGE_MAX  = 0.88f;
    public static final float TERRAIN_RANGE_POWER = 2.2f;            // higher => more "only in cores"
    public static final float TERRAIN_RANGE_EXTRA_AMPLITUDE = 55.0f; // added on top of normal mountains

    // --- Ultra-rare mega peaks (Everest-like) ---
    public static final float TERRAIN_PEAK_FREQ      = 1.0f / 4096.0f; // not too large, rarity comes from threshold+power
    public static final float TERRAIN_PEAK_THRESHOLD = 0.92f;          // higher => rarer
    public static final float TERRAIN_PEAK_POWER     = 10.0f;          // higher => sharper/rarer
    public static final float TERRAIN_PEAK_AMPLITUDE = 120.0f;         // extra blocks for mega peaks


    // ===================== END TERRAIN TUNING =====================




    // ======================= BIOME TUNING =======================

    // Biome size scale - higher = larger biomes (1.0 = base, 2.0 = double size)
    public static final float BIOME_SIZE_SCALE = 2.0f;

    // Climate noise frequencies (smaller freq => larger biomes) - scaled by BIOME_SIZE_SCALE
    public static final float BIOME_TEMP_FREQ   = 1.0f / (8192.0f * BIOME_SIZE_SCALE);
    public static final float BIOME_HUMID_FREQ  = 1.0f / (8192.0f * BIOME_SIZE_SCALE);
    public static final float BIOME_WEIRD_FREQ  = 1.0f / (4096.0f * BIOME_SIZE_SCALE);

    // Continentalness frequency for climate gradients (latitudinal feel)
    public static final float BIOME_CONTINENTAL_FREQ = 1.0f / (16384.0f * BIOME_SIZE_SCALE);

    // Domain warp to make biome borders organic (not straight lines)
    public static final float BIOME_WARP_FREQ       = 1.0f / (4096.0f * BIOME_SIZE_SCALE);
    public static final float BIOME_WARP_AMP_BLOCKS = 300.0f * BIOME_SIZE_SCALE;

    // How much altitude cools temperature (0.0..1.0 range). Higher => more snow on mountains.
    public static final float BIOME_ALTITUDE_COOLING = 0.40f;

    // Swamp generation thresholds
    public static final int SWAMP_MAX_ELEVATION_ABOVE_SEA = 12;  // Swamps only at low elevations
    public static final float SWAMP_MIN_HUMIDITY = 0.55f;         // Swamps require high humidity
    public static final float SWAMP_NOISE_FREQ = 1.0f / (2048.0f * BIOME_SIZE_SCALE);

    // Biome IDs (keep or change as you like, but generator/decorator should match)
    // Climate biomes (land) - driven by temperature/humidity grid
    public static final short BIOME_PLAINS  = 0;
    public static final short BIOME_DESERT  = 1;
    public static final short BIOME_SNOW    = 2;
    public static final short BIOME_FOREST  = 3;
    public static final short BIOME_SAVANNA = 4;
    public static final short BIOME_SWAMP   = 5;
    public static final short BIOME_JUNGLE  = 6;

    // Geographic biomes (structural) - driven by world topology (water bodies)
    public static final short BIOME_OCEAN      = 7;
    public static final short BIOME_DEEP_OCEAN = 8;

    // Ocean depth thresholds
    public static final int DEEP_OCEAN_DEPTH = 40;   // was 20 - Deep ocean starts much further from shore

    // Swamp generation - inland low-lying areas only
    public static final int SWAMP_MIN_DISTANCE_FROM_OCEAN = 16; // Blocks inland from coastline

    // Climate temperature zones (0.0 = coldest, 1.0 = hottest)
    public static final float CLIMATE_COLD_MAX = 0.25f;     // Snow biome threshold
    public static final float CLIMATE_COOL_MAX = 0.42f;     // Plains/Forest threshold
    public static final float CLIMATE_WARM_MAX = 0.58f;     // Savanna threshold (lowered to give more hot biome area)
    // Above CLIMATE_WARM_MAX = hot (Desert/Jungle)

    // Humidity thresholds (0.0 = driest, 1.0 = wettest)
    public static final float CLIMATE_DRY_MAX = 0.38f;      // Desert/Savanna/Plains(dry)
    public static final float CLIMATE_MODERATE_MAX = 0.58f; // Plains/Forest (lowered to give more wet biome area)
    // Above CLIMATE_MODERATE_MAX = wet (Forest/Jungle/Swamp)

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

    // --- Region layer halo (fixes region seam structure cuts) ---
    public static final int REGION_LAYER_PAD_CHUNKS = 1;
    public static final int STRUCTURE_PLACEMENT_HALO_CHUNKS = 1; // 1 is enough for radius<=15; bump to 2 if you make huge canopies
    public static final int REGION_LAYER_PAD_BLOCKS = REGION_LAYER_PAD_CHUNKS * CHUNK_SIZE;


}
