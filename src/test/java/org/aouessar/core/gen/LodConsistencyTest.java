package org.aouessar.core.gen;

import org.aouessar.core.gen.impl.LodWorldSampler;
import org.aouessar.core.gen.impl.RiverColumnSampler;
import org.aouessar.core.gen.impl.SimpleWorldGenerator;
import org.aouessar.core.gen.impl.TerrainColumnSampler;
import org.aouessar.core.world.LodTile;
import org.aouessar.core.world.layers.Heightmap;
import org.aouessar.core.world.layers.LayerRect;
import org.aouessar.shared.EngineConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The far-field LOD must line up exactly with near-field chunks.
 * These tests pin the core guarantee: the region pipeline heightmap and the
 * LOD tile sampler evaluate the same column function.
 */
class LodConsistencyTest {

    private static final long SEED = EngineConfig.WORLD_SEED;

    @Test
    void regionHeightmapMatchesColumnSampler() {
        SimpleWorldGenerator gen = new SimpleWorldGenerator();
        TerrainColumnSampler column = new TerrainColumnSampler(SEED);

        // A rect straddling the origin exercises negative coordinates too
        LayerRect rect = new LayerRect(-24, -24, 48, 48);
        Heightmap hm = gen.generateHeightmap(SEED, rect);

        for (int wz = rect.minZ; wz < rect.maxZExclusive(); wz += 3) {
            for (int wx = rect.minX; wx < rect.maxXExclusive(); wx += 3) {
                assertEquals(column.heightAt(wx, wz), hm.heightAt(wx, wz),
                        "height mismatch at (" + wx + ", " + wz + ")");
            }
        }
    }

    @Test
    void lodTileHeightsMatchColumnSampler() {
        LodWorldSampler lod = new LodWorldSampler(SEED);
        TerrainColumnSampler column = new TerrainColumnSampler(SEED);

        for (int step : new int[]{2, 4, 8, 16, 32}) {
            LodTile tile = lod.sampleTile(-2, 3, step);
            int cells = tile.cells();
            assertEquals(EngineConfig.REGION_SIZE_BLOCKS / step, cells);

            for (int j = -1; j <= cells + 1; j++) {
                for (int i = -1; i <= cells + 1; i++) {
                    int wx = tile.originX() + i * step;
                    int wz = tile.originZ() + j * step;
                    assertEquals(column.heightAt(wx, wz), tile.heightAt(i, j),
                            "LOD height mismatch step=" + step + " at (" + wx + ", " + wz + ")");
                }
            }
        }
    }

    @Test
    void lodTilesAreDeterministic() {
        LodWorldSampler a = new LodWorldSampler(SEED);
        LodWorldSampler b = new LodWorldSampler(SEED);

        LodTile ta = a.sampleTile(5, -7, 8);
        LodTile tb = b.sampleTile(5, -7, 8);

        int cells = ta.cells();
        for (int j = -1; j <= cells + 1; j++) {
            for (int i = -1; i <= cells + 1; i++) {
                assertEquals(ta.heightAt(i, j), tb.heightAt(i, j));
                assertEquals(ta.waterLevelAt(i, j), tb.waterLevelAt(i, j));
                assertEquals(ta.topBlockAt(i, j), tb.topBlockAt(i, j));
            }
        }
    }

    @Test
    void allWaterSitsAtSeaLevel() {
        LodWorldSampler lod = new LodWorldSampler(SEED);
        LodTile tile = lod.sampleTile(0, 0, 8);

        int cells = tile.cells();
        for (int j = 0; j <= cells; j++) {
            for (int i = 0; i <= cells; i++) {
                int h = tile.heightAt(i, j);
                int w = tile.waterLevelAt(i, j);
                if (h < EngineConfig.SEA_LEVEL) {
                    assertEquals(EngineConfig.SEA_LEVEL, w, "water missing at (" + i + "," + j + ")");
                } else {
                    assertEquals(LodTile.NO_WATER, w, "water above sea level at (" + i + "," + j + ")");
                }
            }
        }
    }
}
