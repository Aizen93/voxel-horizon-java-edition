package org.aouessar.core.world.layers;

/**
 * Per-column carve intensity layer.
 * <p>
 * 0 = untouched column; 1..255 = river channel intensity (255 = center line).
 * Carve depth and water level are derived from the intensity, giving channels
 * a real profile: shallow banks, deep center.
 */
public final class CarveMask extends ArrayLayer2D {

    private final byte[] carveIntensity;

    public CarveMask(LayerRect rect, byte[] carveIntensity) {
        super(rect);
        if (carveIntensity.length != rect.sizeX * rect.sizeZ) {
            throw new IllegalArgumentException("CarveMask array length mismatch");
        }
        this.carveIntensity = carveIntensity;
    }

    public boolean isCarvedColumn(int wx, int wz) {
        if (!contains(wx, wz)) return false;
        return carveIntensity[index(wx, wz)] != 0;
    }

    /** Channel intensity in [0..1]; 0 when outside the rect or not carved. */
    public float intensityAt(int wx, int wz) {
        if (!contains(wx, wz)) return 0f;
        return (carveIntensity[index(wx, wz)] & 0xFF) / 255f;
    }

    public byte[] raw() {
        return carveIntensity;
    }
}
