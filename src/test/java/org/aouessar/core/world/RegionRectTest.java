package org.aouessar.core.world;

import org.aouessar.core.world.layers.LayerRect;
import org.aouessar.shared.EngineConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RegionRectTest {

    @Test
    void rectMatchesRegionOriginInBlocks() {
        RegionPos[] samples = {
                new RegionPos(0, 0),
                new RegionPos(1, 2),
                new RegionPos(-1, -2),
                new RegionPos(-3, 4)
        };

        for (RegionPos pos : samples) {
            LayerRect r = RegionRect.rectOf(pos);
            assertEquals(pos.rx() * EngineConfig.REGION_SIZE_BLOCKS, r.minX);
            assertEquals(pos.rz() * EngineConfig.REGION_SIZE_BLOCKS, r.minZ);
            assertEquals(EngineConfig.REGION_SIZE_BLOCKS, r.sizeX);
            assertEquals(EngineConfig.REGION_SIZE_BLOCKS, r.sizeZ);
        }
    }
}