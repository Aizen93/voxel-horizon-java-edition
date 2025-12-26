package org.aouessar.core.gen;

import org.aouessar.core.world.layers.Heightmap;
import org.aouessar.core.world.layers.LayerRect;

public interface WorldGenerator {
    Heightmap generateHeightmap(long seed, LayerRect rect);
}