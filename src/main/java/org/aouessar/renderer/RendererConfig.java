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
    // SKY COLOR config
    //----------------------------------
    public static final float FOGR = 0.55f;
    public static final float FOGG = 0.75f;
    public static final float FOGB = 0.95f;

    //----------------------------------
    // FOG config
    //----------------------------------
    public static final float FOG_ALT_BASE  = 80f; //80f
    public static final float FOG_ALT_RANGE = 200f; //400f

    public static final float FOG_START_LOW  = 260f; //1400f
    public static final float FOG_START_HIGH = 180f; //700f

    public static final float FOG_RANGE_LOW  = 360f; //3600f
    public static final float FOG_RANGE_HIGH = 260f; //2200f

    //----------------------------------
    // SHADERS PATH config
    //----------------------------------
    public static final String VOXEL_TILED_VERT = "/shaders/voxel_tiled.vert";
    public static final String VOXEL_TILED_FRAG = "/shaders/voxel_tiled.frag";
    public static final String VOXEL_TILED_CUTOUT_FRAG = "/shaders/voxel_tiled_cutout.frag";
    public static final String VOXEL_TILED_TRANSLUCENT_FRAG = "/shaders/voxel_tiled_translucent.frag";

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
