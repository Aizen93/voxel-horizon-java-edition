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
    public static final float FOG_START_HIGH = 700f; //700f

    public static final float FOG_RANGE_LOW  = 3600f; //3600f
    public static final float FOG_RANGE_HIGH = 2200f; //2200f

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

    // Horizon band tuning
    public static final float SKY_HORIZON_Y = 0.20f;        // near bottom
    public static final float SKY_HORIZON_SOFTNESS = 0.22f; // larger = wider glow
    public static final float SKY_HORIZON_STRENGTH = 0.95f; // how “sunsety”

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
}