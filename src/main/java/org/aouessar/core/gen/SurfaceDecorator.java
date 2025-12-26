package org.aouessar.core.gen;

import org.aouessar.core.world.layers.BiomeMap;
import org.aouessar.core.world.layers.Heightmap;
import org.aouessar.core.world.layers.SurfaceRules;

public interface SurfaceDecorator {
    SurfaceRules generateSurfaceRules(Heightmap heightmap, BiomeMap biomeMap);
}