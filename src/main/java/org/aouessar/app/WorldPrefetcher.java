package org.aouessar.app;

import org.aouessar.core.api.StreamingRequests;
import org.aouessar.shared.EngineConfig;

public final class WorldPrefetcher {
    private final StreamingRequests requests;
    private final int radiusChunks;

    private int lastCenterCx = Integer.MIN_VALUE;
    private int lastCenterCz = Integer.MIN_VALUE;

    public WorldPrefetcher(StreamingRequests requests, int radiusChunks) {
        this.requests = requests;
        this.radiusChunks = Math.max(0, radiusChunks);
    }

    /** Call once per frame with current world position (camera or player). */
    public void update(float worldX, float worldZ) {
        int cx = (int) Math.floor(worldX / EngineConfig.CHUNK_SIZE);
        int cz = (int) Math.floor(worldZ / EngineConfig.CHUNK_SIZE);

        // Only reschedule when we enter a new center chunk
        if (cx == lastCenterCx && cz == lastCenterCz) return;
        lastCenterCx = cx;
        lastCenterCz = cz;

        // Spiral-ish rings: center first -> outward
        requests.requestChunk(cx, cz);
        for (int r = 1; r <= radiusChunks; r++) {
            for (int dx = -r; dx <= r; dx++) {
                requests.requestChunk(cx + dx, cz - r);
                requests.requestChunk(cx + dx, cz + r);
            }
            for (int dz = -r + 1; dz <= r - 1; dz++) {
                requests.requestChunk(cx - r, cz + dz);
                requests.requestChunk(cx + r, cz + dz);
            }
        }
    }
}