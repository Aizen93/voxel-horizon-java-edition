package org.aouessar.core.gen;

import org.aouessar.core.world.layers.CarveMask;
import org.aouessar.core.world.layers.Heightmap;
import org.aouessar.core.world.layers.WaterLayer;

/**
 * Generates water placement data for a region.
 * <p>
 * This generator runs AFTER terrain, biomes, and carving are complete,
 * so it knows the final surface heights and carved areas (rivers).
 * <p>
 * Water is placed as a layer, not inline in chunk building, enabling:
 * <ul>
 *   <li>Rivers with variable water levels</li>
 *   <li>Lakes at different altitudes</li>
 *   <li>Swamps with shallow water</li>
 *   <li>Cleaner chunk composition</li>
 * </ul>
 */
public interface WaterGenerator {

    /**
     * Generates water level data for each column in the region.
     *
     * @param seed      world seed
     * @param heightmap terrain heights (post-generation)
     * @param carveMask carved areas (rivers, etc.)
     * @return a WaterLayer with water levels per column
     */
    WaterLayer generateWaterLayer(long seed, Heightmap heightmap, CarveMask carveMask);
}
