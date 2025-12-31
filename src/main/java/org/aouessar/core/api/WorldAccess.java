package org.aouessar.core.api;

public record WorldAccess(
        ChunkProvider chunkProvider,
        WorldSampler worldSampler,
        WorldReadiness worldReadiness,
        StreamingRequests streamingRequests
) {}