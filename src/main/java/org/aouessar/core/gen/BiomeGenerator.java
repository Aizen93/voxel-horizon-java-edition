package org.aouessar.core.gen;

import org.aouessar.core.world.layers.BiomeMap;
import org.aouessar.core.world.layers.Heightmap;

public interface BiomeGenerator {
    BiomeMap generateBiomes(long seed, Heightmap heightmap);
}