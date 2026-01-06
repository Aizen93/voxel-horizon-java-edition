package org.aouessar.core.world;

public final class Blocks {
    private Blocks() {}

    public static final short AIR = 0;

    // --- terrain ---
    public static final short GRASS = 1;
    public static final short DIRT = 2;
    public static final short STONE = 3;
    public static final short WATER = 4;
    public static final short SAND = 5;
    public static final short SNOW = 6;          // keep as you had (use as "snow block" for now)
    public static final short GLASS = 7;

    public static final short LEAVES = 8;
    public static final short BUSH = 9;

    // --- new: minecraft-like essentials ---
    public static final short BEDROCK = 10;
    public static final short GRAVEL = 11;
    public static final short CLAY = 12;
    public static final short SANDSTONE = 13;
    public static final short DEEPSLATE = 14;
    public static final short ICE = 15;

    // vegetation / structure blocks
    public static final short TALL_GRASS = 16;
    public static final short FLOWER_RED = 17;
    public static final short FLOWER_YELLOW = 18;

    public static final short OAK_LOG = 19;
    public static final short OAK_LEAVES = 20;

    public static final short CACTUS = 21;
    public static final short DESERT_SAND = 22;
    public static final short DESERT_SANDSTONE = 23;

    public static final short PODZOl_DIRT = 24;
    public static final short SNOW_GRASS = 25;
    public static final short DRY_GRASS = 26;

    public static final short DRY_WHEAT = 27;

    // tree types
    public static final short ACACIA_LOG = 28;
    public static final short ACACIA_LEAVES = 29;

    public static final short JUNGLE_LOG = 30;
    public static final short JUNGLE_LEAVES = 31;

    // structure markers
    public static final short STRUCT_ACACIA_TREE = 101;
    public static final short STRUCT_JUNGLE_TREE = 102;
    public static final short STRUCT_MEGA_JUNGLE = 103;


    // "structure marker ids" (optional): placements can use these ids to request multi-block placement
    public static final short STRUCT_OAK_TREE = 100;

    public static RenderLayer getRenderLayer(short blockId) {
        return switch (blockId) {
            case WATER, GLASS -> RenderLayer.TRANSLUCENT;

            case LEAVES, OAK_LEAVES, BUSH, TALL_GRASS,
                 FLOWER_RED, FLOWER_YELLOW, DRY_WHEAT,
                 JUNGLE_LEAVES, ACACIA_LEAVES -> RenderLayer.CUTOUT;

            default -> RenderLayer.OPAQUE;
        };
    }
}