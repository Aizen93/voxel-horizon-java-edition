package org.aouessar.app;

import org.aouessar.core.api.BiomeLocator;
import org.aouessar.core.api.LodProvider;
import org.aouessar.core.api.WorldAccess;
import org.aouessar.core.gen.RegionPipeline;
import org.aouessar.core.gen.impl.BiomeDecorator;
import org.aouessar.core.gen.impl.DefaultBiomeGenerator;
import org.aouessar.core.gen.impl.DefaultRegionPipeline;
import org.aouessar.core.gen.impl.DefaultStructureBuilder;
import org.aouessar.core.gen.impl.DefaultWaterGenerator;
import org.aouessar.core.gen.impl.DefaultWorldCarver;
import org.aouessar.core.gen.impl.LodWorldSampler;
import org.aouessar.core.gen.impl.SimpleWorldGenerator;
import org.aouessar.core.stream.BiomeLocatorImpl;
import org.aouessar.core.stream.RegionStreamingService;
import org.aouessar.renderer.LwjglRendererV1;

public final class Main {

    public static void main(String[] args) {
        long seed = org.aouessar.shared.EngineConfig.WORLD_SEED;

        // Core pipeline (deterministic, region-based)
        RegionPipeline pipeline = new DefaultRegionPipeline(
            new SimpleWorldGenerator(),
            new DefaultBiomeGenerator(),
            new DefaultWorldCarver(),
            new BiomeDecorator(),
            new DefaultWaterGenerator(),
            new DefaultStructureBuilder()
        );

        RegionStreamingService world = new RegionStreamingService(seed, pipeline);

        BiomeLocator biomeLocator = new BiomeLocatorImpl(world);

        // Far-field LOD: pure direct sampling, independent of the region cache
        LodProvider lodProvider = new LodWorldSampler(seed);

        WorldAccess access = new WorldAccess(world, world, biomeLocator, world, lodProvider);

        // Renderer v1: near-field voxel chunks + far-field LOD rings.
        // 16 chunks (256 blocks) of full voxel detail before the LOD takes over.
        new LwjglRendererV1(access, 16).run();
    }
}