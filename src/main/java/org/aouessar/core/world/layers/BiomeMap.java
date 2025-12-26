package org.aouessar.core.world.layers;

public final class BiomeMap extends ArrayLayer2D {
    private final short[] biomeId;

    public BiomeMap(LayerRect rect, short[] biomeId) {
        super(rect);
        if (biomeId.length != rect.sizeX * rect.sizeZ) {
            throw new IllegalArgumentException("BiomeMap array length mismatch");
        }
        this.biomeId = biomeId;
    }

    /** Safe: returns 0 (default biome) if outside. */
    public short biomeIdAt(int wx, int wz) {
        if (!contains(wx, wz)) return 0;
        return biomeId[index(wx, wz)];
    }

    public short biomeIdAtUnchecked(int wx, int wz) {
        return biomeId[index(wx, wz)];
    }

    public short[] rawBiomeIds() {
        return biomeId;
    }
}