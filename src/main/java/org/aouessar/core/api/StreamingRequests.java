package org.aouessar.core.api;

import org.aouessar.core.world.RegionPos;

public interface StreamingRequests {

    /** Non-blocking: schedule region generation if not ready/in-flight. */
    void requestRegion(RegionPos rp);

    /** Non-blocking: schedule the owning region of this chunk. */
    void requestChunk(int cx, int cz);

    /** Non-blocking: schedule the owning region of this world column. */
    void requestColumn(int wx, int wz);
}