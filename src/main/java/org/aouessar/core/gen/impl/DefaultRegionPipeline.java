package org.aouessar.core.gen.impl;

import org.aouessar.core.gen.*;
import org.aouessar.core.world.layers.LayerRect;
import org.aouessar.core.world.layers.RegionLayers;

public final class DefaultRegionPipeline implements RegionPipeline {

    private final WorldGenerator worldGenerator;
    private final BiomeGenerator biomeGenerator;
    private final WorldCarver worldCarver;
    private final SurfaceDecorator surfaceDecorator;
    private final WaterGenerator waterGenerator;
    private final StructureBuilder structureBuilder;

    public DefaultRegionPipeline(
            WorldGenerator worldGenerator,
            BiomeGenerator biomeGenerator,
            WorldCarver worldCarver,
            SurfaceDecorator surfaceDecorator,
            WaterGenerator waterGenerator,
            StructureBuilder structureBuilder
    ) {
        this.worldGenerator = worldGenerator;
        this.biomeGenerator = biomeGenerator;
        this.worldCarver = worldCarver;
        this.surfaceDecorator = surfaceDecorator;
        this.waterGenerator = waterGenerator;
        this.structureBuilder = structureBuilder;
    }

    @Override
    public RegionLayers generateRegionLayers(long seed, LayerRect rect) {
        var heightmap = worldGenerator.generateHeightmap(seed, rect);
        var biomes = biomeGenerator.generateBiomes(seed, heightmap);
        var carve = worldCarver.generateCarveMask(seed, heightmap);
        var surface = surfaceDecorator.generateSurfaceRules(seed, heightmap, biomes);
        var water = waterGenerator.generateWaterLayer(seed, heightmap, carve);
        var structures = structureBuilder.placeStructures(seed, heightmap, biomes);
        return new RegionLayers(heightmap, biomes, carve, surface, water, structures);
    }
}