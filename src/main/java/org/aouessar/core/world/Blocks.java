package org.aouessar.core.world;

public final class Blocks {
    private Blocks() {}

    public static final short AIR = 0;
    public static final short GRASS = 1;
    public static final short DIRT = 2;
    public static final short STONE = 3;
    public static final short WATER = 4;
    public static final short SAND = 5;
    public static final short SNOW = 6;
    public static final short GLASS = 7;
    public static final short LEAVES = 8;
    public static final short BUSH = 9;

    public static RenderLayer getRenderLayer(short blockId) {
        return switch (blockId) {
            case WATER, GLASS -> RenderLayer.TRANSLUCENT;
            case LEAVES, BUSH -> RenderLayer.CUTOUT;
            default -> RenderLayer.OPAQUE;
        };
    }
}