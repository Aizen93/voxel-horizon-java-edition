package org.aouessar.core.world.layers;

public abstract class ArrayLayer2D {
    protected final LayerRect rect;

    protected ArrayLayer2D(LayerRect rect) {
        this.rect = rect;
    }

    public final LayerRect rect() {
        return rect;
    }

    protected final int index(int wx, int wz) {
        // This method is hot. Keep it tiny.
        int lx = wx - rect.minX;
        int lz = wz - rect.minZ;
        return lz * rect.sizeX + lx;
    }

    protected final boolean contains(int wx, int wz) {
        return rect.contains(wx, wz);
    }
}