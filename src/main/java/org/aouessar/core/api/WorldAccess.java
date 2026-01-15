package org.aouessar.core.api;

public record WorldAccess(
        ChunkProvider chunkProvider,
        WorldSampler worldSampler,
        BiomeLocator biomeLocator,
        StreamingControl streamingControl
) {}