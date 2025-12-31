package org.aouessar.core.api;

import org.aouessar.core.world.RegionPos;

public interface WorldReadiness {

    boolean isRegionReady(RegionPos rp);

    boolean isChunkReady(int cx, int cz);

    /**
     * Monotonic revision for ready regions.
     * 0 means: not ready / unknown.
     */
    long regionRevision(RegionPos rp);

    long chunkRevision(int cx, int cz);
}