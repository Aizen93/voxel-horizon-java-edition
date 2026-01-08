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

    // Dense defaults (tune later)
    private static final int TREES_TRIES_PER_CHUNK   = 6;
    private static final int PLANTS_TRIES_PER_CHUNK  = 14; // tallgrass / drywheat
    private static final int FLOWERS_TRIES_PER_CHUNK = 5;
    private static final int BUSH_TRIES_PER_CHUNK    = 5;
    private static final int CACTUS_TRIES_PER_CHUNK  = 4;

    @Override
    public StructureMap placeStructures(long seed, Heightmap heightmap, BiomeMap biomeMap) {
        LayerRect rect = heightmap.rect();
        List<StructureMap.Placement> placements = new ArrayList<>(rect.sizeX * rect.sizeZ / 48);

        final int minChunkX = Math.floorDiv(rect.minX, EngineConfig.CHUNK_SIZE);
        final int minChunkZ = Math.floorDiv(rect.minZ, EngineConfig.CHUNK_SIZE);
        final int maxChunkX = Math.floorDiv(rect.maxXExclusive() - 1, EngineConfig.CHUNK_SIZE);
        final int maxChunkZ = Math.floorDiv(rect.maxZExclusive() - 1, EngineConfig.CHUNK_SIZE);

        for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
            for (int cx = minChunkX; cx <= maxChunkX; cx++) {

                // ----- Trees (biome-specific types) -----
                for (int t = 0; t < TREES_TRIES_PER_CHUNK; t++) {
                    long h = GlobalTerrainUtils.hash(seed, cx, cz, 0x10_0000 + t);

                    int wx = cx * EngineConfig.CHUNK_SIZE + (int) (h & 15);
                    int wz = cz * EngineConfig.CHUNK_SIZE + (int) ((h >>> 4) & 15);
                    if (!rect.contains(wx, wz)) continue;

                    int surfaceY = heightmap.heightAt(wx, wz);
                    if (surfaceY <= EngineConfig.SEA_LEVEL + 1) continue;

                    short biome = biomeMap.biomeIdAtUnchecked(wx, wz);

                    short marker = getMarker(h, biome);

                    if (marker == 0) continue;

                    placements.add(new StructureMap.Placement(wx, surfaceY + 1, wz, marker));
                }

                // ----- Plants (tall grass / dry wheat) -----
                for (int t = 0; t < PLANTS_TRIES_PER_CHUNK; t++) {
                    long h = GlobalTerrainUtils.hash(seed, cx, cz, 0x30_0000 + t);

                    int wx = cx * EngineConfig.CHUNK_SIZE + (int) (h & 15);
                    int wz = cz * EngineConfig.CHUNK_SIZE + (int) ((h >>> 4) & 15);
                    if (!rect.contains(wx, wz)) continue;

                    int surfaceY = heightmap.heightAt(wx, wz);
                    if (surfaceY <= EngineConfig.SEA_LEVEL) continue;

                    short biome = biomeMap.biomeIdAtUnchecked(wx, wz);

                    // No plants in desert, and snow is optional (keep off for now)
                    if (biome == EngineConfig.BIOME_DESERT || biome == EngineConfig.BIOME_SNOW) continue;

                    int r = (int) ((h >>> 12) & 0xFF);

                    short plant;
                    switch (biome) {
                        case EngineConfig.BIOME_SAVANNA -> {
                            // dry grass => dry wheat instead of tall grass
                            if (r > 235) continue;
                            plant = Blocks.DRY_WHEAT;
                        }
                        case EngineConfig.BIOME_JUNGLE -> {
                            // jungle very dense undergrowth
                            if (r > 245) continue;
                            plant = Blocks.TALL_GRASS;
                        }
                        default -> {
                            // plains/forest/swamp
                            if (r > 235) continue;
                            plant = Blocks.TALL_GRASS;
                        }
                    }

                    placements.add(new StructureMap.Placement(wx, surfaceY + 1, wz, plant));
                }

                // ----- Flowers (mainly plains/forest) -----
                for (int t = 0; t < FLOWERS_TRIES_PER_CHUNK; t++) {
                    long h = GlobalTerrainUtils.hash(seed, cx, cz, 0x40_0000 + t);

                    int wx = cx * EngineConfig.CHUNK_SIZE + (int) (h & 15);
                    int wz = cz * EngineConfig.CHUNK_SIZE + (int) ((h >>> 4) & 15);
                    if (!rect.contains(wx, wz)) continue;

                    int surfaceY = heightmap.heightAt(wx, wz);
                    if (surfaceY <= EngineConfig.SEA_LEVEL) continue;

                    short biome = biomeMap.biomeIdAtUnchecked(wx, wz);
                    if (biome != EngineConfig.BIOME_PLAINS && biome != EngineConfig.BIOME_FOREST) continue;

                    int r = (int) ((h >>> 12) & 0xFF);
                    if (r > 180) continue;

                    short flower = (((h >>> 20) & 1) == 0) ? Blocks.FLOWER_RED : Blocks.FLOWER_YELLOW;

                    placements.add(new StructureMap.Placement(wx, surfaceY + 1, wz, flower));
                }

                // ----- Bushes (swamp + jungle) -----
                for (int t = 0; t < BUSH_TRIES_PER_CHUNK; t++) {
                    long h = GlobalTerrainUtils.hash(seed, cx, cz, 0x50_0000 + t);

                    int wx = cx * EngineConfig.CHUNK_SIZE + (int) (h & 15);
                    int wz = cz * EngineConfig.CHUNK_SIZE + (int) ((h >>> 4) & 15);
                    if (!rect.contains(wx, wz)) continue;

                    int surfaceY = heightmap.heightAt(wx, wz);
                    if (surfaceY <= EngineConfig.SEA_LEVEL) continue;

                    short biome = biomeMap.biomeIdAtUnchecked(wx, wz);
                    int r = (int) ((h >>> 12) & 0xFF);

                    boolean allow =
                            (biome == EngineConfig.BIOME_SWAMP  && r < 240) ||
                                    (biome == EngineConfig.BIOME_JUNGLE && r < 200);

                    if (!allow) continue;

                    placements.add(new StructureMap.Placement(
                            wx, surfaceY + 1, wz, Blocks.BUSH
                    ));
                }

                // ----- Cactus (desert) -----
                for (int t = 0; t < CACTUS_TRIES_PER_CHUNK; t++) {
                    long h = GlobalTerrainUtils.hash(seed, cx, cz, 0x20_0000 + t);

                    int wx = cx * EngineConfig.CHUNK_SIZE + (int) (h & 15);
                    int wz = cz * EngineConfig.CHUNK_SIZE + (int) ((h >>> 4) & 15);
                    if (!rect.contains(wx, wz)) continue;

                    int surfaceY = heightmap.heightAt(wx, wz);
                    if (surfaceY <= EngineConfig.SEA_LEVEL - 1) continue;

                    short biome = biomeMap.biomeIdAtUnchecked(wx, wz);
                    if (biome != EngineConfig.BIOME_DESERT) continue;

                    int r = (int) ((h >>> 12) & 0xFF);
                    if (r > 150) continue;

                    placements.add(new StructureMap.Placement(
                            wx, surfaceY + 1, wz, Blocks.CACTUS
                    ));
                }
            }
        }

        return new StructureMap(rect, placements);
    }

    private static short getMarker(long h, short biome) {
        int r = (int) ((h >>> 12) & 0xFF);

        // Choose tree marker by biome
        short marker = 0;

        switch (biome) {
            case EngineConfig.BIOME_FOREST -> {
                if (r < 235) marker = Blocks.STRUCT_OAK_TREE; // very dense oak
            }
            case EngineConfig.BIOME_PLAINS -> {
                if (r < 90) marker = Blocks.STRUCT_OAK_TREE;  // some oak
            }
            case EngineConfig.BIOME_SAVANNA -> {
                if (r < 140) marker = Blocks.STRUCT_ACACIA_TREE; // dense acacia
            }
            case EngineConfig.BIOME_JUNGLE -> {
                // Jungle: lots of jungle trees + rare mega
                if (r < 220) marker = Blocks.STRUCT_JUNGLE_TREE;
                // mega jungle very rare
                int r2 = (int) ((h >>> 20) & 0xFF);
                if (r2 < 12) marker = Blocks.STRUCT_MEGA_JUNGLE;
            }
            case EngineConfig.BIOME_SWAMP -> {
                if (r < 80) marker = Blocks.STRUCT_OAK_TREE; // some trees
            }
            default -> {
                // snow/desert: no trees for now
            }
        }
        return marker;
    }
}