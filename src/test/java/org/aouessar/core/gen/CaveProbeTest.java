package org.aouessar.core.gen;

import org.aouessar.core.gen.impl.BiomeDecorator;
import org.aouessar.core.gen.impl.DefaultBiomeGenerator;
import org.aouessar.core.gen.impl.DefaultRegionPipeline;
import org.aouessar.core.gen.impl.DefaultStructureBuilder;
import org.aouessar.core.gen.impl.DefaultWaterGenerator;
import org.aouessar.core.gen.impl.DefaultWorldCarver;
import org.aouessar.core.gen.impl.SimpleWorldGenerator;
import org.aouessar.core.stream.RegionStreamingService;
import org.aouessar.core.world.Blocks;
import org.aouessar.core.world.RegionPos;
import org.aouessar.core.world.chunk.Chunk;
import org.aouessar.shared.EngineConfig;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Debug probe (not an assertion test): scans chunks around the default camera
 * spot for cave entrances and prints coordinates to visit in-game.
 * <p>
 * Land entrance  = column whose heightmap surface block got carved to AIR.
 * Water entrance = column under sea whose bed got carved (WATER below the bed).
 */
public class CaveProbeTest {

    @Test
    @Disabled("debug probe, not a regression test — remove the annotation to scan for cave entrances")
    public void findCaveEntrances() throws Exception {
        long seed = EngineConfig.WORLD_SEED;

        var pipeline = new DefaultRegionPipeline(
                new SimpleWorldGenerator(),
                new DefaultBiomeGenerator(),
                new DefaultWorldCarver(),
                new BiomeDecorator(),
                new DefaultWaterGenerator(),
                new DefaultStructureBuilder()
        );

        try (RegionStreamingService world = new RegionStreamingService(seed, pipeline)) {
            // Scan a few regions around the world origin (the usual camera area)
            final int regionRadius = 2;
            int landSpots = 0;
            int waterSpots = 0;
            long airBelowSurface = 0;

            for (int rz = -regionRadius; rz <= regionRadius; rz++) {
                for (int rx = -regionRadius; rx <= regionRadius; rx++) {
                    world.ensureRegion(new RegionPos(rx, rz)).get();
                }
            }

            final int chunkRadius = regionRadius * EngineConfig.REGION_SIZE_CHUNKS;
            for (int cz = -chunkRadius; cz < chunkRadius; cz++) {
                for (int cx = -chunkRadius; cx < chunkRadius; cx++) {
                    Chunk chunk = world.getChunk(cx, cz);

                    // Count entrance columns per chunk to report only real holes
                    int landCols = 0;
                    int waterCols = 0;
                    int sx = 0, sy = 0, sz = 0, swx = 0, swy = 0, swz = 0;

                    for (int lz = 0; lz < 16; lz++) {
                        for (int lx = 0; lx < 16; lx++) {
                            int wx = cx * 16 + lx;
                            int wz = cz * 16 + lz;
                            int surface = world.heightAt(wx, wz);

                            short atSurface = chunk.getBlock(lx, surface, lz);
                            if (atSurface == Blocks.AIR) {
                                landCols++;
                                sx = wx; sy = surface; sz = wz;
                            } else if (surface < EngineConfig.SEA_LEVEL
                                    && chunk.getBlock(lx, surface - 2, lz) == Blocks.WATER) {
                                waterCols++;
                                swx = wx; swy = surface; swz = wz;
                            }

                            // Any carved air well below the surface (cave volume proxy)
                            for (int y = surface - 12; y > surface - 40; y -= 4) {
                                if (y > EngineConfig.MIN_Y + 4 && chunk.getBlock(lx, y, lz) == Blocks.AIR) {
                                    airBelowSurface++;
                                    break;
                                }
                            }
                        }
                    }

                    if (landCols >= 8) {
                        landSpots++;
                        System.out.println("[cave] LAND entrance  ~" + landCols + " cols at ("
                                + sx + ", " + sy + ", " + sz + ")  elev+" + (sy - EngineConfig.SEA_LEVEL));
                    }
                    if (waterCols >= 8) {
                        waterSpots++;
                        System.out.println("[cave] WATER entrance ~" + waterCols + " cols at ("
                                + swx + ", " + swy + ", " + swz + ")  depth" + (swy - EngineConfig.SEA_LEVEL));
                    }
                }
            }

            System.out.println("[cave] summary: " + landSpots + " land entrance chunks, "
                    + waterSpots + " water entrance chunks, "
                    + airBelowSurface + " columns with cave air below the surface");
        }
    }
}
