package org.aouessar.core.world;

import org.aouessar.core.world.layers.LayerRect;
import org.aouessar.shared.EngineConfig;

/**
 * Builds the fixed LayerRect for a region.
 * Regions own finite array-backed layers; LayerRect maps world coords to array indices.
 */
public final class RegionRect {
    private RegionRect() {}

    public static LayerRect rectOf(RegionPos pos) {
        int minX = pos.rx() * EngineConfig.REGION_SIZE_BLOCKS;
        int minZ = pos.rz() * EngineConfig.REGION_SIZE_BLOCKS;
        int size = EngineConfig.REGION_SIZE_BLOCKS;
        return new LayerRect(minX, minZ, size, size);
    }
}