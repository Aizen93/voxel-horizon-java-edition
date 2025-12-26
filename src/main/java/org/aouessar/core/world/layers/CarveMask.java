package org.aouessar.core.world.layers;

public final class CarveMask extends ArrayLayer2D {

    // 0 = not carved, 1 = carved column (e.g., river channel)
    private final byte[] carvedColumn;

    public CarveMask(LayerRect rect, byte[] carvedColumn) {
        super(rect);
        if (carvedColumn.length != rect.sizeX * rect.sizeZ) {
            throw new IllegalArgumentException("CarveMask array length mismatch");
        }
        this.carvedColumn = carvedColumn;
    }

    public boolean isCarvedColumn(int wx, int wz) {
        if (!contains(wx, wz)) return false;
        return carvedColumn[index(wx, wz)] != 0;
    }

    public byte[] raw() {
        return carvedColumn;
    }
}