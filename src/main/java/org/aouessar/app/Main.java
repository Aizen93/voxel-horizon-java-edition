package org.aouessar.app;

import org.aouessar.core.api.BiomeLocator;
import org.aouessar.core.api.WorldAccess;
import org.aouessar.core.gen.RegionPipeline;
import org.aouessar.core.gen.impl.BiomeDecorator;
import org.aouessar.core.gen.impl.DefaultBiomeGenerator;
import org.aouessar.core.gen.impl.DefaultRegionPipeline;
import org.aouessar.core.gen.impl.DefaultStructureBuilder;
import org.aouessar.core.gen.impl.DefaultWorldCarver;
import org.aouessar.core.gen.impl.SimpleWorldGenerator;
import org.aouessar.core.stream.BiomeLocatorImpl;
import org.aouessar.core.stream.RegionStreamingService;
import org.aouessar.renderer.LwjglRendererV1;

public final class Main {

    public static void main(String[] args) {
        long seed = 905282311L; //548511558115960005L

        // Core pipeline (deterministic, region-based)
        RegionPipeline pipeline = new DefaultRegionPipeline(
            new SimpleWorldGenerator(),
            new DefaultBiomeGenerator(),
            new DefaultWorldCarver(),
            new BiomeDecorator(),
            new DefaultStructureBuilder()
        );

        RegionStreamingService world = new RegionStreamingService(seed, pipeline);

        BiomeLocator biomeLocator = new BiomeLocatorImpl(world);

        WorldAccess access = new WorldAccess(world, world, biomeLocator);

        // Renderer v1 (near-field)
        new LwjglRendererV1(access, 32).run();
    }
}