package org.aouessar.core.world;

import org.aouessar.shared.EngineConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WorldGridTest {

    @Test
    void localInChunkIsAlwaysInRange_evenForNegativeWorldCoords() {
        int[] samples = { -33, -32, -31, -17, -16, -15, -1, 0, 1, 15, 16, 17, 31, 32, 33 };

        for (int wx : samples) {
            int lx = WorldGrid.worldBlockToLocalInChunkX(wx);
            assertTrue(lx >= 0 && lx < EngineConfig.CHUNK_SIZE, "wx=" + wx + " -> lx=" + lx);
        }
    }

    @Test
    void worldBlockToChunkAndBack_roundTripsWithinChunkBounds() {
        int[] samples = { -1000, -33, -32, -1, 0, 1, 31, 32, 1000 };

        for (int wx : samples) {
            int cx = WorldGrid.worldBlockToChunkX(wx);
            int origin = WorldGrid.chunkOriginBlockX(cx);
            assertTrue(wx >= origin && wx < origin + EngineConfig.CHUNK_SIZE,
                    "wx=" + wx + " cx=" + cx + " origin=" + origin);
        }
    }

    @Test
    void chunkToRegionAndLocalIsAlwaysInRange_evenForNegativeChunkCoords() {
        int[] samples = { -100, -33, -32, -17, -16, -1, 0, 1, 15, 16, 17, 32, 33, 100 };

        for (int c : samples) {
            int local = WorldGrid.chunkToLocalInRegionX(c);
            assertTrue(local >= 0 && local < EngineConfig.REGION_SIZE_CHUNKS,
                    "c=" + c + " -> local=" + local);
        }
    }
}
