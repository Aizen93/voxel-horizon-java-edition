package org.aouessar.app;

import org.aouessar.core.api.ChunkProvider;
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
        long seed = 12345L;

        // Core pipeline (deterministic, region-based)
        RegionPipeline pipeline = new DefaultRegionPipeline(
                new SimpleWorldGenerator(),
                new DefaultBiomeGenerator(),
                new DefaultWorldCarver(),
                new BiomeDecorator(),
                new DefaultStructureBuilder()
        );

        // Core streaming service (ChunkProvider)
        ChunkProvider chunkProvider = new RegionStreamingService(seed, pipeline);

        // Renderer v1 (near-field)
        new LwjglRendererV1(chunkProvider, 32).run();
    }
}
