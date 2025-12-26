package org.aouessar.core.gen.impl;

import org.aouessar.core.gen.StructureBuilder;
import org.aouessar.core.world.layers.BiomeMap;
import org.aouessar.core.world.layers.Heightmap;
import org.aouessar.core.world.layers.StructureMap;

import java.util.List;

public final class DefaultStructureBuilder implements StructureBuilder {

    @Override
    public StructureMap placeStructures(long seed, Heightmap heightmap, BiomeMap biomeMap) {
        // Production-ready contract: immutable list, deterministic placement later.
        return new StructureMap(heightmap.rect(), List.of());
    }
}