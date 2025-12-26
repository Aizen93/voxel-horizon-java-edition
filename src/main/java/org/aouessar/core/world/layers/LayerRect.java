package org.aouessar.core.world.layers;

/**
 * Internal bounds helper for array-backed layers.
 * Not visible to renderer. Not a "world window".
 *
 * minX/minZ are world-block coordinates of the layer origin.
 * sizeX/sizeZ are extents in blocks.
 */
public final class LayerRect {
    public final int minX;
    public final int minZ;
    public final int sizeX;
    public final int sizeZ;

    public LayerRect(int minX, int minZ, int sizeX, int sizeZ) {
        if (sizeX <= 0 || sizeZ <= 0) throw new IllegalArgumentException("Invalid rect size");
        this.minX = minX;
        this.minZ = minZ;
        this.sizeX = sizeX;
        this.sizeZ = sizeZ;
    }

    public int maxXExclusive() { return minX + sizeX; }
    public int maxZExclusive() { return minZ + sizeZ; }

    public boolean contains(int wx, int wz) {
        return wx >= minX && wx < maxXExclusive() && wz >= minZ && wz < maxZExclusive();
    }

    /** World->local X coordinate in [0..sizeX-1], assumes contains(). */
    public int localX(int wx) { return wx - minX; }

    /** World->local Z coordinate in [0..sizeZ-1], assumes contains(). */
    public int localZ(int wz) { return wz - minZ; }
}