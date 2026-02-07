package org.aouessar.core.world.layers;

public final class SurfaceRules extends ArrayLayer2D {

    private final short[] topBlock;     // e.g. GRASS/SAND/SNOW
    private final short[] fillerBlock;  // e.g. DIRT
    private final byte[] fillerDepth;   // e.g. 3..6

    public SurfaceRules(LayerRect rect, short[] topBlock, short[] fillerBlock, byte[] fillerDepth) {
        super(rect);
        int n = rect.sizeX * rect.sizeZ;
        if (topBlock.length != n || fillerBlock.length != n || fillerDepth.length != n) {
            throw new IllegalArgumentException("SurfaceRules array length mismatch");
        }
        this.topBlock = topBlock;
        this.fillerBlock = fillerBlock;
        this.fillerDepth = fillerDepth;
    }

    public short topBlockAt(int wx, int wz) {
        if (!contains(wx, wz)) return 0;
        return topBlock[index(wx, wz)];
    }

    public short fillerBlockAt(int wx, int wz) {
        if (!contains(wx, wz)) return 0;
        return fillerBlock[index(wx, wz)];
    }

    public int fillerDepthAt(int wx, int wz) {
        if (!contains(wx, wz)) return 0;
        return fillerDepth[index(wx, wz)] & 0xFF;
    }

    public short[] rawTop() {
        return topBlock;
    }

    public short[] rawFiller() {
        return fillerBlock;
    }

    public byte[] rawDepth() {
        return fillerDepth;
    }
}