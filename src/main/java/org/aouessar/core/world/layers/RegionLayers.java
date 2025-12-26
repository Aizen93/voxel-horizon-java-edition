package org.aouessar.core.world.layers;

public record RegionLayers(
        Heightmap heightmap,
        BiomeMap biomeMap,
        CarveMask carveMask,
        SurfaceRules surfaceRules,
        StructureMap structureMap
) {}