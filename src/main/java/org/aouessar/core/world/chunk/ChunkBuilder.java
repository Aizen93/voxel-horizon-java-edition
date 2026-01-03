package org.aouessar.core.world.chunk;

import org.aouessar.core.world.Blocks;
import org.aouessar.core.world.Region;
import org.aouessar.core.world.WorldGrid;
import org.aouessar.core.world.layers.RegionLayers;
import org.aouessar.core.world.layers.StructureMap;
import org.aouessar.shared.EngineConfig;

import java.util.ArrayList;
import java.util.List;

public final class ChunkBuilder {

    public Chunk buildChunk(Region region, int cx, int cz) {
        short[] blocks = new short[EngineConfig.CHUNK_SIZE * EngineConfig.CHUNK_SIZE * EngineConfig.WORLD_HEIGHT];
        Chunk chunk = new Chunk(cx, cz, blocks);

        RegionLayers layers = region.layers();

        final int chunkOriginX = WorldGrid.chunkOriginBlockX(cx);
        final int chunkOriginZ = WorldGrid.chunkOriginBlockZ(cz);

        final int sea = EngineConfig.SEA_LEVEL;

        // ---- 1) Prefilter placements for this chunk ONCE (no O(columns*placements)) ----
        List<StructureMap.Placement> localPlacements = filterPlacementsForChunk(layers.structureMap(), cx, cz);

        // ---- 2) Terrain fill ----
        for (int lz = 0; lz < EngineConfig.CHUNK_SIZE; lz++) {
            int wz = chunkOriginZ + lz;

            for (int lx = 0; lx < EngineConfig.CHUNK_SIZE; lx++) {
                int wx = chunkOriginX + lx;

                int surfaceY = layers.heightmap().heightAt(wx, wz);
                short biome = layers.biomeMap().biomeIdAtUnchecked(wx, wz);

                // surface rules (already biome-aware via your decorator)
                short top = layers.surfaceRules().topBlockAt(wx, wz);
                short filler = layers.surfaceRules().fillerBlockAt(wx, wz);
                int depth = layers.surfaceRules().fillerDepthAt(wx, wz);

                boolean carvedRiver = layers.carveMask().isCarvedColumn(wx, wz);

                // Beach / shoreline override (helps blending + feels Minecraft)
                boolean isBeach = Math.abs(surfaceY - sea) <= EngineConfig.BEACH_BAND;
                if (isBeach && surfaceY >= sea - 2 && surfaceY <= sea + 6) {
                    top = Blocks.SAND;
                    filler = Blocks.SAND;
                    depth = Math.max(depth, 4);
                }

                // Desert: sand + sandstone under it
                boolean isDesert = (biome == EngineConfig.BIOME_DESERT);
                if (isDesert && !isBeach) {
                    top = Blocks.DESERT_SAND;
                    filler = Blocks.DESERT_SAND;
                    depth = Math.max(depth, 6);
                }

                // Underwater seabed: sand/gravel/clay mix
                boolean underwater = surfaceY < sea;
                if (underwater) {
                    int h = hash8(wx, wz);
                    if (h < EngineConfig.OCEAN_CLAY_CHANCE_PER_256) {
                        top = Blocks.CLAY;
                        filler = Blocks.CLAY;
                        depth = Math.max(depth, 3);
                    } else if (h < EngineConfig.OCEAN_CLAY_CHANCE_PER_256 + EngineConfig.OCEAN_GRAVEL_CHANCE_PER_256) {
                        top = Blocks.GRAVEL;
                        filler = Blocks.GRAVEL;
                        depth = Math.max(depth, 3);
                    } else {
                        top = Blocks.SAND;
                        filler = Blocks.SAND;
                        depth = Math.max(depth, 4);
                    }
                }

                // Rivers: carve down a little and make a riverbed + water
                // (Your CarveMask is "isCarvedColumn", so treat it as “river column”)
                int carvedSurfaceY = surfaceY;
                if (carvedRiver && surfaceY >= sea - 12) {
                    carvedSurfaceY = surfaceY - EngineConfig.RIVER_CARVE_DEPTH;
                    if (carvedSurfaceY < EngineConfig.MIN_Y + EngineConfig.BEDROCK_THICKNESS) {
                        carvedSurfaceY = EngineConfig.MIN_Y + EngineConfig.BEDROCK_THICKNESS;
                    }
                }

                // Now fill column MIN_Y..MAX_Y
                for (int wy = EngineConfig.MIN_Y; wy <= EngineConfig.MAX_Y; wy++) {
                    short id;

                    // Bedrock base
                    if (wy < EngineConfig.MIN_Y + EngineConfig.BEDROCK_THICKNESS) {
                        id = Blocks.BEDROCK;
                        chunk.setBlock(lx, wy, lz, id);
                        continue;
                    }

                    // Above surface => air / water
                    if (wy > carvedSurfaceY) {
                        // Water rules:
                        // - if below/at sea level => water, but rivers in cold biomes can freeze on top
                        if (wy <= sea) {
                            // Optional: freeze surface water in snow biome
                            if ((biome == EngineConfig.BIOME_SNOW) && wy == sea) {
                                id = Blocks.ICE;
                            } else {
                                id = Blocks.WATER;
                            }
                        } else {
                            id = Blocks.AIR;
                        }

                        chunk.setBlock(lx, wy, lz, id);
                        continue;
                    }

                    // Solid below surface
                    int below = carvedSurfaceY - wy;

                    // Riverbed override: top few blocks become gravel/sand
                    if (carvedRiver && below <= EngineConfig.RIVERBED_THICKNESS) {
                        id = underwater ? Blocks.GRAVEL : Blocks.GRAVEL;
                        chunk.setBlock(lx, wy, lz, id);
                        continue;
                    }

                    // Top / filler / stone strata
                    if (wy == carvedSurfaceY) {
                        // Snow biome can use snow top above sea
                        if (biome == EngineConfig.BIOME_SNOW && carvedSurfaceY > sea + 8) {
                            id = Blocks.SNOW;
                        } else {
                            id = top;
                        }
                    } else if (below <= depth) {
                        // Sand -> sandstone transition (beach/ocean sand vs desert sand)
                        if (below > 2) {
                            if (filler == Blocks.DESERT_SAND) id = Blocks.DESERT_SANDSTONE;
                            else if (filler == Blocks.SAND) id = Blocks.SANDSTONE;
                            else id = filler;
                        } else {
                            id = filler;
                        }
                    } else {
                        // deep stone strata
                        if (wy <= EngineConfig.DEEPSLATE_START_Y) id = Blocks.DEEPSLATE;
                        else id = Blocks.STONE;
                    }

                    chunk.setBlock(lx, wy, lz, id);
                }
            }
        }

        // ---- 3) Structures placement (once, after terrain fill) ----
        for (StructureMap.Placement p : localPlacements) {
            applyPlacement(chunk, cx, cz, p);
        }

        return chunk;
    }

    // -------------------------------------------------------------------------
    // Placements
    // -------------------------------------------------------------------------

    private static List<StructureMap.Placement> filterPlacementsForChunk(StructureMap map, int cx, int cz) {
        // You currently expose placements() iterable; we filter once here.
        List<StructureMap.Placement> out = new ArrayList<>();
        for (StructureMap.Placement p : map.placements()) {
            if (Math.floorDiv(p.wx(), 16) == cx && Math.floorDiv(p.wz(), 16) == cz) {
                out.add(p);
            }
        }
        return out;
    }

    private static void applyPlacement(Chunk chunk, int cx, int cz, StructureMap.Placement p) {
        // World -> local
        int lx = Math.floorMod(p.wx(), 16);
        int lz = Math.floorMod(p.wz(), 16);
        int wy = p.wy();

        if (wy < EngineConfig.MIN_Y || wy > EngineConfig.MAX_Y) return;

        short marker = p.structureId();

        // Must have air at placement point
        if (chunk.getBlock(lx, wy, lz) != Blocks.AIR) return;

        // Ground check (common)
        short ground = (wy - 1 >= EngineConfig.MIN_Y) ? chunk.getBlock(lx, wy - 1, lz) : Blocks.AIR;

        // --- Multi-block “structure markers” ---
        if (marker == Blocks.STRUCT_OAK_TREE) {
            // Allow trees on any soil-like block
            if (!isSoil(ground)) return;

            int h = 4 + (hash8(p.wx(), p.wz()) % 3); // 4..6
            placeOakTreePart(chunk, cx, cz, p.wx(), wy, p.wz(), h);
            return;
        }

        // --- Single-block placements (vegetation) ---
        if (marker == Blocks.TALL_GRASS || marker == Blocks.FLOWER_RED || marker == Blocks.FLOWER_YELLOW || marker == Blocks.BUSH) {
            // Plants should grow on grass-like tops (not podzol, not dirt)
            if (!isGrassLike(ground)) return;

            chunk.setBlock(lx, wy, lz, marker);
            return;
        }

        // --- Cactus (2-4 tall) ---
        if (marker == Blocks.CACTUS) {
            // Desert sand should allow cactus too
            if (!isSandLike(ground)) return;

            int height = 2 + (hash8(p.wx(), p.wz()) % 3); // 2..4
            for (int dy = 0; dy < height; dy++) {
                int y = wy + dy;
                if (y > EngineConfig.MAX_Y) break;
                if (chunk.getBlock(lx, y, lz) != Blocks.AIR) break;
                chunk.setBlock(lx, y, lz, Blocks.CACTUS);
            }
            return;
        }

        // Fallback: treat structureId as a direct block id (your current behavior)
        chunk.setBlock(lx, wy, lz, marker);
    }

    /**
     * Places only the part of the oak tree that belongs to THIS chunk.
     * This allows trees near borders to be placed consistently when neighbor chunks build.
     */
    private static void placeOakTreePart(Chunk chunk, int cx, int cz, int wxBase, int wyBase, int wzBase, int trunkH) {
        // Trunk
        for (int dy = 0; dy < trunkH; dy++) {
            setIfInThisChunk(chunk, cx, cz, wxBase, wyBase + dy, wzBase, Blocks.OAK_LOG);
        }

        // Leaves blob (simple, good-looking)
        int crownY = wyBase + trunkH - 1;
        for (int dz = -2; dz <= 2; dz++) {
            for (int dx = -2; dx <= 2; dx++) {
                int dist2 = dx * dx + dz * dz;
                if (dist2 > 6) continue;

                // two layers of leaves
                setLeavesIfAir(chunk, cx, cz, wxBase + dx, crownY,     wzBase + dz);
                setLeavesIfAir(chunk, cx, cz, wxBase + dx, crownY + 1, wzBase + dz);

                // top cap (smaller)
                if (dist2 <= 2) {
                    setLeavesIfAir(chunk, cx, cz, wxBase + dx, crownY + 2, wzBase + dz);
                }
            }
        }
    }

    private static void setLeavesIfAir(Chunk chunk, int cx, int cz, int wx, int wy, int wz) {
        if (wy < EngineConfig.MIN_Y || wy > EngineConfig.MAX_Y) return;
        if (!isInChunk(wx, wz, cx, cz)) return;

        int lx = Math.floorMod(wx, 16);
        int lz = Math.floorMod(wz, 16);

        if (chunk.getBlock(lx, wy, lz) == Blocks.AIR) {
            chunk.setBlock(lx, wy, lz, Blocks.OAK_LEAVES);
        }
    }

    private static void setIfInThisChunk(Chunk chunk, int cx, int cz, int wx, int wy, int wz, short id) {
        if (wy < EngineConfig.MIN_Y || wy > EngineConfig.MAX_Y) return;
        if (!isInChunk(wx, wz, cx, cz)) return;

        int lx = Math.floorMod(wx, 16);
        int lz = Math.floorMod(wz, 16);

        chunk.setBlock(lx, wy, lz, id);
    }

    private static boolean isInChunk(int wx, int wz, int cx, int cz) {
        return Math.floorDiv(wx, 16) == cx && Math.floorDiv(wz, 16) == cz;
    }

    // Small deterministic hash -> [0..255]
    private static int hash8(int x, int z) {
        int h = x * 0x1f1f1f1f ^ z * 0x7f4a7c15;
        h ^= (h >>> 16);
        h *= 0x85ebca6b;
        h ^= (h >>> 13);
        return h & 0xFF;
    }

    private static boolean isSoil(short id) {
        return id == Blocks.DIRT ||
                id == Blocks.GRASS ||
                id == Blocks.DRY_GRASS ||
                id == Blocks.SNOW_GRASS ||
                id == Blocks.PODZOl_DIRT;
    }

    private static boolean isGrassLike(short id) {
        return id == Blocks.GRASS ||
                id == Blocks.DRY_GRASS ||
                id == Blocks.SNOW_GRASS;
    }

    private static boolean isSandLike(short id) {
        return id == Blocks.SAND ||
                id == Blocks.DESERT_SAND;
    }
}