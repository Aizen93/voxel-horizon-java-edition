package org.aouessar.core.api;

import org.aouessar.core.world.LodTile;

/**
 * Far-field LOD sampling service.
 * <p>
 * Unlike {@link ChunkProvider}, this does NOT depend on region generation or
 * caching: tiles are computed directly from the deterministic column functions
 * (seed + coordinates only), so any tile at any distance can be sampled at any
 * time, from any thread, without touching the region cache. This is what makes
 * Distant Horizons-scale view distances affordable: a step-16 tile costs a few
 * hundred noise evaluations instead of a full 288x288x6-layer region build.
 * <p>
 * Guarantees:
 * <ul>
 *   <li>Pure and deterministic: same (seed, tile, step) always yields the same tile</li>
 *   <li>Heights are byte-identical to the full-resolution region pipeline</li>
 *   <li>Thread-safe; never blocks on world state; never throws for any coordinate</li>
 * </ul>
 */
public interface LodProvider {

    /**
     * Sample one LOD tile.
     *
     * @param tileX tile X (same coordinate space as regions)
     * @param tileZ tile Z (same coordinate space as regions)
     * @param step  sample spacing in blocks; must divide REGION_SIZE_BLOCKS (e.g. 4, 8, 16, 32)
     */
    LodTile sampleTile(int tileX, int tileZ, int step);
}
