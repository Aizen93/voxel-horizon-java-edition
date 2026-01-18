package org.aouessar.core.gen.impl;

import org.aouessar.core.gen.WaterGenerator;
import org.aouessar.core.world.layers.CarveMask;
import org.aouessar.core.world.layers.Heightmap;
import org.aouessar.core.world.layers.LayerRect;
import org.aouessar.core.world.layers.WaterLayer;
import org.aouessar.shared.EngineConfig;

/**
 * Default water generator that places water based on:
 * <ul>
 *   <li>Sea level (ocean/seas)</li>
 *   <li>Carved river channels</li>
 * </ul>
 * <p>
 * This implementation maintains the current Minecraft-like behavior
 * but encapsulates it in a dedicated layer for cleaner architecture.
 */
public final class DefaultWaterGenerator implements WaterGenerator {

    @Override
    public WaterLayer generateWaterLayer(long seed, Heightmap heightmap, CarveMask carveMask) {
        LayerRect rect = heightmap.rect();
        int n = rect.sizeX * rect.sizeZ;
        int[] waterLevel = new int[n];

        final int sea = EngineConfig.SEA_LEVEL;

        int i = 0;
        for (int z = 0; z < rect.sizeZ; z++) {
            int wz = rect.minZ + z;
            for (int x = 0; x < rect.sizeX; x++) {
                int wx = rect.minX + x;

                int surfaceY = heightmap.heightAtUnchecked(wx, wz);
                boolean carvedRiver = carveMask.isCarvedColumn(wx, wz);

                // Determine water level for this column
                int level = computeWaterLevel(surfaceY, carvedRiver, sea);
                waterLevel[i++] = level;
            }
        }

        return new WaterLayer(rect, waterLevel);
    }

    /**
     * Computes the water level for a single column.
     *
     * @param surfaceY    the terrain surface height
     * @param carvedRiver true if this column is a carved river
     * @param seaLevel    the global sea level
     * @return water level Y, or {@link WaterLayer#NO_WATER} if dry
     */
    private int computeWaterLevel(int surfaceY, boolean carvedRiver, int seaLevel) {
        // Ocean/sea: water fills to sea level if terrain is below
        if (surfaceY < seaLevel) {
            return seaLevel;
        }

        // River: carved columns near sea level get water
        // Rivers carve terrain down, then water fills to (roughly) sea level
        if (carvedRiver && surfaceY >= seaLevel - 12) {
            // River water level matches sea level to ensure connectivity
            return seaLevel;
        }

        // Above sea level and not a river => no water
        return WaterLayer.NO_WATER;
    }
}
