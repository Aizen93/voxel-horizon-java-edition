package org.aouessar.core.api;

/**
 * Control interface for streaming/caching subsystem.
 * Allows renderer to request eviction without knowing core internals.
 */
public interface StreamingControl {

    /**
     * Evict regions and chunks outside the given radius from the center chunk.
     * @param centerCx center chunk X
     * @param centerCz center chunk Z
     * @param radiusChunks eviction radius in chunks
     */
    void evictOutside(int centerCx, int centerCz, int radiusChunks);

    /**
     * @return number of cached regions (for debug)
     */
    int regionCount();

    /**
     * @return number of cached chunks (for debug)
     */
    int chunkCount();
}

