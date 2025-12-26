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

    // Vanilla-ish default sea level used by many tools/resources is 62 :contentReference[oaicite:2]{index=2}
    public static final int SEA_LEVEL = 62;

    // -----------------------------
    // LOD (renderer-owned policy values; core doesn't care)
    // -----------------------------
    // Near: full chunk meshes
    public static final int NEAR_RADIUS_CHUNKS = 24;

    // Mid: blocky surface tiles from sampler (no chunk meshes)
    public static final int MID_RADIUS_BLOCKS = 512;          // tweak freely
    public static final int MID_TILE_SIZE_BLOCKS = 64;         // renderer tile size
    public static final int MID_HEIGHT_QUANT = 1;              // 1=blocky accurate, 2/4=steppier

    // Far: heightmap terrain tiles
    public static final int FAR_RADIUS_BLOCKS = 2048;          // tweak freely
    public static final int FAR_TILE_SIZE_SAMPLES = 128;       // grid resolution per tile at LOD0

    // LOD step per ring (grid spacing in blocks for far tiles)
    public static final int[] FAR_LOD_STEPS = { 16, 32, 64 };

    // -----------------------------
    // Threading (core + renderer)
    // -----------------------------
    public static final int CPU_WORKERS = Math.max(2, Runtime.getRuntime().availableProcessors() - 1);

    // --- Renderer budgets / throttles ---
    public static final int UPLOAD_QUEUE_CAPACITY = 256;
    public static final int MAX_UPLOADS_PER_FRAME = 10;

    // Reduce caches while stabilizing (tune later)
    public static final int NEAR_CACHE_MAX = 256;
    public static final int MID_CACHE_MAX  = 512;
    public static final int FAR_CACHE_MAX  = 768;

    // Limit near meshing vertical range (temporary until section-based meshing)
    public static final int MESH_MAX_Y = SEA_LEVEL + 64;

    public static final int MID_SAMPLE_STEP_BLOCKS = 8;   // tune
    public static final float MID_SKIRT_BOTTOM_Y = MIN_Y;
}
