package org.aouessar.core.world;

import org.aouessar.core.world.layers.LayerRect;
import org.aouessar.core.world.layers.RegionLayers;

/**
 * Streaming unit: immutable once created.
 * Owns its LayerRect + RegionLayers.
 */
public final class Region {
    private final RegionPos pos;
    private final LayerRect rect;
    private final RegionLayers layers;

    public Region(RegionPos pos, LayerRect rect, RegionLayers layers) {
        this.pos = pos;
        this.rect = rect;
        this.layers = layers;
    }

    public RegionPos pos() {
        return pos;
    }

    public LayerRect rect() {
        return rect;
    }

    public RegionLayers layers() {
        return layers;
    }
}