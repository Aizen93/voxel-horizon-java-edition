package org.aouessar.core.gen;

import org.aouessar.core.world.layers.CarveMask;
import org.aouessar.core.world.layers.Heightmap;

public interface WorldCarver {
    CarveMask generateCarveMask(long seed, Heightmap heightmap);
}