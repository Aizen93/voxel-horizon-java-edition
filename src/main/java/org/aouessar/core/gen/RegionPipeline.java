package org.aouessar.core.gen;

import org.aouessar.core.world.layers.LayerRect;
import org.aouessar.core.world.layers.RegionLayers;

public interface RegionPipeline {
    RegionLayers generateRegionLayers(long seed, LayerRect rect);
}