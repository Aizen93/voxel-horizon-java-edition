package org.aouessar.core.gen.impl;

import org.aouessar.core.gen.StructureBuilder;
import org.aouessar.core.world.Blocks;
import org.aouessar.core.world.layers.BiomeMap;
import org.aouessar.core.world.layers.Heightmap;
import org.aouessar.core.world.layers.LayerRect;
import org.aouessar.core.world.layers.StructureMap;
import org.aouessar.shared.EngineConfig;

import java.util.ArrayList;
import java.util.List;

public final class DefaultStructureBuilder implements StructureBuilder {

    private static final int CHUNK_SIZE = 16;

    // Try counts per chunk (tune later)
    private static final int TREES_TRIES_PER_CHUNK   = 6;
    private static final int CACTUS_TRIES_PER_CHUNK  = 4;
    private static final int GRASS_TRIES_PER_CHUNK   = 14;
    private static final int FLOWER_TRIES_PER_CHUNK  = 5;
    private static final int BUSH_TRIES_PER_CHUNK    = 5;

    @Override
    public StructureMap placeStructures(long seed, Heightmap heightmap, BiomeMap biomeMap) {
        LayerRect rect = heightmap.rect();
        List<StructureMap.Placement> placements = new ArrayList<>(rect.sizeX * rect.sizeZ / 64);

        final int minChunkX = Math.floorDiv(rect.minX, CHUNK_SIZE);
        final int minChunkZ = Math.floorDiv(rect.minZ, CHUNK_SIZE);
        final int maxChunkX = Math.floorDiv(rect.maxXExclusive() - 1, CHUNK_SIZE);
        final int maxChunkZ = Math.floorDiv(rect.maxZExclusive() - 1, CHUNK_SIZE);

        for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
            for (int cx = minChunkX; cx <= maxChunkX; cx++) {

                // --- Trees (forest/plains) ---
                for (int t = 0; t < TREES_TRIES_PER_CHUNK; t++) {
                    long h = hash(seed, cx, cz, 0x10_0000 + t);
                    int wx = cx * CHUNK_SIZE + (int) (h & 15);
                    int wz = cz * CHUNK_SIZE + (int) ((h >>> 4) & 15);
                    if (!rect.contains(wx, wz)) continue;

                    int surfaceY = heightmap.heightAt(wx, wz);
                    if (surfaceY <= EngineConfig.SEA_LEVEL + 1) continue; // avoid water/beaches

                    short biome = biomeMap.biomeIdAtUnchecked(wx, wz);

                    // spawn trees primarily in forest, occasionally in plains/savanna
                    // (adjust these to taste)
                    int r = (int) ((h >>> 12) & 0xFF);
                    boolean allow =
                            (biome == EngineConfig.BIOME_FOREST  && r < 230) || // very dense
                            (biome == EngineConfig.BIOME_PLAINS  && r < 90)  || // moderate
                            (biome == EngineConfig.BIOME_SAVANNA && r < 70)  || // some
                            (biome == EngineConfig.BIOME_SWAMP   && r < 80);    // some

                    if (!allow) continue;

                    // Place a multi-block tree marker.
                    placements.add(new StructureMap.Placement(
                            wx,
                            surfaceY + 1,
                            wz,
                            Blocks.STRUCT_OAK_TREE
                    ));
                }

                // --- Cactus (desert) ---
                for (int t = 0; t < CACTUS_TRIES_PER_CHUNK; t++) {
                    long h = hash(seed, cx, cz, 0x20_0000 + t);
                    int wx = cx * CHUNK_SIZE + (int) (h & 15);
                    int wz = cz * CHUNK_SIZE + (int) ((h >>> 4) & 15);
                    if (!rect.contains(wx, wz)) continue;

                    int surfaceY = heightmap.heightAt(wx, wz);
                    if (surfaceY <= EngineConfig.SEA_LEVEL - 1) continue;

                    short biome = biomeMap.biomeIdAtUnchecked(wx, wz);
                    if (biome != EngineConfig.BIOME_DESERT) continue;

                    // sparse cactus
                    int r = (int) ((h >>> 12) & 0xFF);
                    if (r > 150) continue;

                    placements.add(new StructureMap.Placement(
                            wx,
                            surfaceY + 1,
                            wz,
                            Blocks.CACTUS
                    ));
                }

                // --- Tall grass (plains/savanna/forest edges) ---
                for (int t = 0; t < GRASS_TRIES_PER_CHUNK; t++) {
                    long h = hash(seed, cx, cz, 0x30_0000 + t);
                    int wx = cx * CHUNK_SIZE + (int) (h & 15);
                    int wz = cz * CHUNK_SIZE + (int) ((h >>> 4) & 15);
                    if (!rect.contains(wx, wz)) continue;

                    int surfaceY = heightmap.heightAt(wx, wz);
                    if (surfaceY <= EngineConfig.SEA_LEVEL) continue;

                    short biome = biomeMap.biomeIdAtUnchecked(wx, wz);

                    if (biome == EngineConfig.BIOME_DESERT || biome == EngineConfig.BIOME_SNOW) continue;

                    // density varies by biome
                    int r = (int) ((h >>> 12) & 0xFF);
                    boolean allow =
                            (biome == EngineConfig.BIOME_PLAINS  && r < 235) || // very dense
                            (biome == EngineConfig.BIOME_SAVANNA && r < 210) || // dense
                            (biome == EngineConfig.BIOME_FOREST  && r < 170) || // moderate (forest floor)
                            (biome == EngineConfig.BIOME_SWAMP   && r < 220);   // dense

                    if (!allow) continue;

                    placements.add(new StructureMap.Placement(
                            wx,
                            surfaceY + 1,
                            wz,
                            Blocks.TALL_GRASS
                    ));
                }

                // --- Flowers (plains/forest) ---
                for (int t = 0; t < FLOWER_TRIES_PER_CHUNK; t++) {
                    long h = hash(seed, cx, cz, 0x40_0000 + t);
                    int wx = cx * CHUNK_SIZE + (int) (h & 15);
                    int wz = cz * CHUNK_SIZE + (int) ((h >>> 4) & 15);
                    if (!rect.contains(wx, wz)) continue;

                    int surfaceY = heightmap.heightAt(wx, wz);
                    if (surfaceY <= EngineConfig.SEA_LEVEL) continue;

                    short biome = biomeMap.biomeIdAtUnchecked(wx, wz);
                    if (biome != EngineConfig.BIOME_PLAINS && biome != EngineConfig.BIOME_FOREST) continue;

                    int r = (int) ((h >>> 12) & 0xFF);
                    if (r > 180) continue;

                    short flower = (((h >>> 20) & 1) == 0) ? Blocks.FLOWER_RED : Blocks.FLOWER_YELLOW;

                    placements.add(new StructureMap.Placement(
                            wx,
                            surfaceY + 1,
                            wz,
                            flower
                    ));
                }

                // --- Bushes (swamp + forest understory) ---
                for (int t = 0; t < BUSH_TRIES_PER_CHUNK; t++) {
                    long h = hash(seed, cx, cz, 0x50_0000 + t);
                    int wx = cx * CHUNK_SIZE + (int) (h & 15);
                    int wz = cz * CHUNK_SIZE + (int) ((h >>> 4) & 15);
                    if (!rect.contains(wx, wz)) continue;

                    int surfaceY = heightmap.heightAt(wx, wz);
                    if (surfaceY <= EngineConfig.SEA_LEVEL) continue;

                    short biome = biomeMap.biomeIdAtUnchecked(wx, wz);

                    int r = (int) ((h >>> 12) & 0xFF);
                    boolean allow =
                            (biome == EngineConfig.BIOME_SWAMP  && r < 240) || // very dense
                            (biome == EngineConfig.BIOME_FOREST && r < 140);   // more forest understory

                    if (!allow) continue;

                    placements.add(new StructureMap.Placement(
                            wx,
                            surfaceY + 1,
                            wz,
                            Blocks.BUSH
                    ));
                }
            }
        }

        return new StructureMap(rect, placements);
    }

    private static long hash(long seed, int cx, int cz, int salt) {
        long v = seed;
        v ^= (long) cx * 0x632BE59BD9B4E019L;
        v ^= (long) cz * 0x9E3779B97F4A7C15L;
        v ^= (long) salt * 0x85157AF5L;
        v ^= (v >>> 27);
        v *= 0x94D049BB133111EBL;
        return v ^ (v >>> 31);
    }
}