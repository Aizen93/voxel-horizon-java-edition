package org.aouessar.core.gen;

import org.aouessar.core.world.layers.BiomeMap;
import org.aouessar.core.world.layers.Heightmap;
import org.aouessar.core.world.layers.StructureMap;

public interface StructureBuilder {
    StructureMap placeStructures(long seed, Heightmap heightmap, BiomeMap biomeMap);
}