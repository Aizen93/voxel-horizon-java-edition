package org.aouessar.app;

import org.aouessar.core.api.WorldAccess;
import org.aouessar.core.gen.RegionPipeline;
import org.aouessar.core.gen.impl.BiomeDecorator;
import org.aouessar.core.gen.impl.DefaultBiomeGenerator;
import org.aouessar.core.gen.impl.DefaultRegionPipeline;
import org.aouessar.core.gen.impl.DefaultStructureBuilder;
import org.aouessar.core.gen.impl.DefaultWorldCarver;
import org.aouessar.core.gen.impl.SimpleWorldGenerator;
import org.aouessar.core.stream.RegionStreamingService;
import org.aouessar.renderer.LwjglRendererV1;

public final class Main {

    public static void main(String[] args) {
        long seed = 905282311L;

        // Core pipeline (deterministic, region-based)
        RegionPipeline pipeline = new DefaultRegionPipeline(
                new SimpleWorldGenerator(),
                new DefaultBiomeGenerator(),
                new DefaultWorldCarver(),
                new BiomeDecorator(),
                new DefaultStructureBuilder()
        );

        // Core streaming service (concrete lives ONLY in app)
        RegionStreamingService core = new RegionStreamingService(seed, pipeline);

        // WorldAccess = single boundary object for everything outside core
        WorldAccess world = new WorldAccess(
            core, // ChunkProvider
            core, // WorldSampler
            core, // WorldReadiness
            core  // StreamingRequests
        );

        // App-owned prefetch policy (no renderer->core coupling)
        WorldPrefetcher prefetcher = new WorldPrefetcher(world.streamingRequests(), 40);

        // Renderer v1 (near-field)
        new LwjglRendererV1(world, prefetcher::update, 32).run();
    }
}