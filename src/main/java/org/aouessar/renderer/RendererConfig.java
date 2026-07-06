package org.aouessar.renderer;

public final class RendererConfig {
    private RendererConfig() {}

    //----------------------------------
    // UI WINDOW config
    //----------------------------------
    public static final int WINDOW_WIDTH = 1280;
    public static final int WINDOW_HEIGHT = 720;
    public static final String WINDOW_TITLE = "Voxel Renderer v1 (Near Field)";

    //----------------------------------
    // SKY / FOG config
    //----------------------------------
    public static final float FOG_ALT_BASE  = 80f; //80f
    public static final float FOG_ALT_RANGE = 400f; //400f

    public static final float FOG_START_LOW  = 1400f; //1400f
    // From altitude you should SEE the far-field horizon, not a fog wall.
    // Full fog must still land inside the LOD ring so the world edge is never
    // visible: start + range < LOD_VIEW_TILES * 256 * sqrt(2).
    public static final float FOG_START_HIGH = 1800f; //700f pre-LOD

    public static final float FOG_RANGE_LOW  = 3600f; //3600f
    public static final float FOG_RANGE_HIGH = 3400f; //2200f pre-LOD

    // Fog cycle --> Weather
    public static final float DAY_LENGTH_SECONDS = 300f;
    public static final float SUNRISE_SUNSET_WIDTH = 0.25f;
    public static final float FOG_TWILIGHT_GLOW = 0.30f;   // try 0.15..0.45
    public static final float SKY_TWILIGHT_BOOST = 0.45f;


    public static final float FOG_DAY_R = 0.55f;
    public static final float FOG_DAY_G = 0.75f;
    public static final float FOG_DAY_B = 0.95f;

    public static final float FOG_NIGHT_R = 0.03f;
    public static final float FOG_NIGHT_G = 0.05f;
    public static final float FOG_NIGHT_B = 0.10f;

    public static final float FOG_WARM_R = 1.00f;
    public static final float FOG_WARM_G = 0.58f;
    public static final float FOG_WARM_B = 0.18f;
    public static final float FOG_WARM_STRENGTH = 0.75f;

    // Weather multipliers (start + range)
    public static final float RAIN_FOG_START_MUL = 0.70f;
    public static final float RAIN_FOG_RANGE_MUL = 0.55f;

    public static final float MIST_FOG_START_MUL = 0.95f;
    public static final float MIST_FOG_RANGE_MUL = 0.85f;

    // Temporary debug hooks (replace with real weather system later)
    public static final float DEBUG_RAIN = 0.0f; // set 0..1
    public static final float DEBUG_MIST = 1.0f; // set 0..1

    // Day/night terrain lighting (multiplies all world shading)
    /** Light level at deep night (0 = pitch black, 1 = full day). */
    public static final float NIGHT_LIGHT_FLOOR = 0.18f;
    /** How strongly sunrise/sunset tint the terrain orange (0..1). */
    public static final float TWILIGHT_SUNLIGHT_TINT = 0.55f;

    // Clouds (volumetric slab in the sky shader + drifting terrain shadows)
    public static final float CLOUD_COVER = 0.55f;          // 0 = clear, 1 = overcast
    public static final float CLOUD_HEIGHT = 460f;          // render-space Y of the deck
    public static final float CLOUD_SHADOW_STRENGTH = 0.4f; // terrain darkening under clouds

    // Camera planes (shared by projection + water depth linearization)
    public static final float CAMERA_NEAR = 0.1f;
    public static final float CAMERA_FAR = 8000f;

    //----------------------------------
    // HDR post-processing
    //----------------------------------
    public static final float POST_EXPOSURE = 1.6f;    // pre-tonemap exposure
    public static final float BLOOM_THRESHOLD = 1.0f;  // HDR luma where bloom starts
    public static final float BLOOM_STRENGTH = 0.55f;
    public static final float GODRAY_STRENGTH = 0.5f;

    // Temporal antialiasing: subpixel-jittered projection + history blend.
    // TAA_BLEND is the share of the CURRENT frame (lower = smoother/softer).
    // Overridable per run with -Pvoxel.taa=false (debugging).
    public static final boolean TAA_ENABLED =
            Boolean.parseBoolean(System.getProperty("voxel.taa", "true"));
    public static final float TAA_BLEND = 0.12f;

    public static final String POST_VERT = "/shaders/post_fullscreen.vert";
    public static final String POST_BRIGHT_FRAG = "/shaders/post_bright.frag";
    public static final String POST_BLUR_FRAG = "/shaders/post_blur.frag";
    public static final String POST_RAYS_FRAG = "/shaders/post_godrays.frag";
    public static final String POST_TAA_FRAG = "/shaders/post_taa.frag";
    public static final String POST_COMPOSITE_FRAG = "/shaders/post_composite.frag";

    //----------------------------------
    // Handheld torch (cave exploration)
    //----------------------------------
    /** How far the torch light reaches (blocks). */
    public static final float TORCH_RANGE_BLOCKS = 28f;
    /** Warm flame color. */
    public static final float TORCH_R = 1.00f;
    public static final float TORCH_G = 0.72f;
    public static final float TORCH_B = 0.42f;
    /** Light intensity at the torch (multiplies albedo, HDR). */
    public static final float TORCH_INTENSITY = 1.15f;

    public static final String TORCH_HAND_VERT = "/shaders/torch_hand.vert";
    public static final String TORCH_HAND_FRAG = "/shaders/torch_hand.frag";

    //----------------------------------
    // Underwater (camera below the water surface)
    //----------------------------------
    /** God rays keep working under water as light shafts, but dimmer. */
    public static final float UNDERWATER_GODRAY_MUL = 0.55f;

    //----------------------------------
    // SHADERS PATH config
    //----------------------------------
    public static final String VOXEL_TILED_VERT = "/shaders/voxel_tiled.vert";
    public static final String VOXEL_TILED_FRAG = "/shaders/voxel_tiled.frag";
    public static final String VOXEL_TILED_CUTOUT_FRAG = "/shaders/voxel_tiled_cutout.frag";
    public static final String VOXEL_TILED_TRANSLUCENT_FRAG = "/shaders/voxel_tiled_translucent.frag";
    public static final String SKY_VERT = "/shaders/sky_fullscreen.vert";
    public static final String SKY_FRAG = "/shaders/sky_fullscreen.frag";

    //----------------------------------
    // ATLAS PATH config
    //----------------------------------
    public static final String ATLAS_JSON = "/atlas.json";
    public static final String ATLAS_PNG = "/atlas.png";
    public static final float ATLAS_TILE_SIZE = 16f;
    public static final float ATLAS_WIDTH = 320f;
    public static final float ATLAS_HEIGHT = 320f;

    //----------------------------------
    // Budget per frame config
    //----------------------------------
    public static final int SUBMIT_BUDGET_PER_FRAME = 64;
    public static final int UPLOAD_BUDGET_PER_FRAME = 128;

    //----------------------------------
    // Performance tuning
    //----------------------------------
    /** How often to run eviction (in frames). Higher = less CPU overhead but more memory */
    public static final int EVICT_INTERVAL_FRAMES = 30;

    /** Max in-flight mesh builds per cache */
    public static final int MAX_IN_FLIGHT_MESHES = 128;

    //----------------------------------
    // Far-field LOD (Distant Horizons rings)
    //----------------------------------
    /**
     * LOD view distance in tiles (1 tile = 256 blocks). 16 tiles = 4096 blocks
     * = 256 chunks of visible terrain in every direction.
     */
    public static final int LOD_VIEW_TILES = 16;

    /** Worker threads for LOD tile sampling + meshing. */
    public static final int LOD_WORKER_THREADS = 2;

    public static final int LOD_SUBMIT_BUDGET_PER_FRAME = 8;
    public static final int LOD_UPLOAD_BUDGET_PER_FRAME = 16;
    public static final int LOD_MAX_IN_FLIGHT = 32;

    /**
     * LOD fragments closer than this many chunks (Chebyshev, in blocks at
     * runtime) are discarded — the near-field voxel chunks own that area.
     * Must stay below the near-field radius so there is never a gap.
     */
    public static final float LOD_NEAR_CUT_MARGIN_CHUNKS = 1.5f;

    public static final String LOD_TERRAIN_VERT = "/shaders/lod_terrain.vert";
    public static final String LOD_TERRAIN_FRAG = "/shaders/lod_terrain.frag";
    public static final String LOD_WATER_FRAG   = "/shaders/lod_water.frag";

    //----------------------------------
    // Near->far dissolve band
    //----------------------------------
    /**
     * Width (blocks) of the dithered band where the near field melts into the
     * LOD. The band ends half a chunk inside the near radius.
     */
    public static final float NEAR_FADE_BAND_BLOCKS = 44f;

    //----------------------------------
    // Sun shadows (cascaded)
    //----------------------------------
    public static final boolean SHADOWS_ENABLED = true;
    public static final int SHADOW_MAP_SIZE = 2048;

    /**
     * Half-extents (blocks) of the ortho box each cascade covers.
     * Cascade 0 = sharp close-ups, 1 = the whole near field, 2 = far field
     * (LOD terrain casts into it), so mountains shade valleys past 1 km.
     */
    public static final float[] SHADOW_CASCADE_EXTENTS = {130f, 420f, 1300f};

    /** Max darkening from shadows (0..1). */
    public static final float SHADOW_STRENGTH = 0.85f;

    public static final String SHADOW_VERT = "/shaders/shadow_depth.vert";
    public static final String SHADOW_FRAG = "/shaders/shadow_depth.frag";
    public static final String LOD_SHADOW_VERT = "/shaders/lod_shadow.vert";
    public static final String LOD_SHADOW_FRAG = "/shaders/lod_shadow.frag";
}