package org.aouessar.core.world.chunk;

import org.aouessar.core.gen.impl.CaveCarver;
import org.aouessar.core.math.GlobalTerrainUtils;
import org.aouessar.core.world.Blocks;
import org.aouessar.core.world.Region;
import org.aouessar.core.world.WorldGrid;
import org.aouessar.core.world.layers.RegionLayers;
import org.aouessar.core.world.layers.StructureMap;
import org.aouessar.core.world.layers.WaterLayer;
import org.aouessar.shared.EngineConfig;

import java.util.ArrayList;
import java.util.List;

public final class ChunkBuilder {

    public Chunk buildChunk(long seed, Region region, int cx, int cz) {
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

                // Water level from the WaterLayer (NO_WATER if dry)
                int waterLevel = layers.waterLayer().waterLevelAtUnchecked(wx, wz);
                boolean hasWater = (waterLevel != WaterLayer.NO_WATER);

                // ---- Ocean biomes: seabed materials ----
                boolean isOceanBiome = (biome == EngineConfig.BIOME_OCEAN || biome == EngineConfig.BIOME_DEEP_OCEAN);

                if (isOceanBiome) {
                    int h = GlobalTerrainUtils.hash8(wx, wz);
                    if (biome == EngineConfig.BIOME_DEEP_OCEAN) {
                        // Deep ocean: more gravel and clay
                        if (h < 80) {
                            top = Blocks.CLAY;
                            filler = Blocks.CLAY;
                        } else if (h < 180) {
                            top = Blocks.GRAVEL;
                            filler = Blocks.GRAVEL;
                        } else {
                            top = Blocks.SAND;
                            filler = Blocks.SAND;
                        }
                    } else {
                        // Regular ocean: more sand
                        if (h < EngineConfig.OCEAN_CLAY_CHANCE_PER_256) {
                            top = Blocks.CLAY;
                            filler = Blocks.CLAY;
                        } else if (h < EngineConfig.OCEAN_CLAY_CHANCE_PER_256 + EngineConfig.OCEAN_GRAVEL_CHANCE_PER_256) {
                            top = Blocks.GRAVEL;
                            filler = Blocks.GRAVEL;
                        } else {
                            top = Blocks.SAND;
                            filler = Blocks.SAND;
                        }
                    }
                    depth = Math.max(depth, 4);
                }

                // ---- Beach terrain feature: sand near shorelines ----
                // Applied to land near sea level, regardless of biome
                boolean isBeachTerrain = !isOceanBiome
                        && Math.abs(surfaceY - sea) <= EngineConfig.BEACH_BAND
                        && surfaceY >= sea - 2 && surfaceY <= sea + 6;
                if (isBeachTerrain) {
                    top = Blocks.SAND;
                    filler = Blocks.SAND;
                    depth = Math.max(depth, 4);
                }

                // ---- Mountain terrain decoration ----
                // Real mountains: steep cliff faces = exposed stone, flat areas = snow/grass
                // This creates the natural look where snow sits on flat surfaces but not on cliffs
                int elevationAboveSea = surfaceY - sea;
                boolean isMountainTerrain = elevationAboveSea >= EngineConfig.MOUNTAIN_MIN_ELEVATION
                        && !isOceanBiome && !isBeachTerrain;

                if (isMountainTerrain) {
                    // Calculate steepness by checking neighboring heights
                    int steepness = computeSteepness(layers, wx, wz, surfaceY);

                    // Use hash for natural variation
                    int mountainHash = GlobalTerrainUtils.hash8(wx, wz);

                    // Steepness thresholds
                    boolean isCliff = steepness >= 6;        // Steep cliff face
                    boolean isSteepSlope = steepness >= 3;   // Moderate slope
                    boolean isFlat = steepness <= 2;         // Flat or gentle slope

                    boolean aboveSnowLine = elevationAboveSea >= EngineConfig.MOUNTAIN_SNOW_LINE;
                    boolean highAlpine = elevationAboveSea >= EngineConfig.MOUNTAIN_HIGH_ALPINE;

                    // ---- Cliff faces: always exposed stone (snow can't stick) ----
                    if (isCliff) {
                        top = Blocks.STONE;
                        filler = Blocks.STONE;
                        depth = 1;
                    }
                    // ---- Steep slopes: mostly stone with some gravel ----
                    else if (isSteepSlope) {
                        if (mountainHash < 180) {
                            top = Blocks.STONE;
                            filler = Blocks.STONE;
                            depth = 1;
                        } else {
                            top = Blocks.GRAVEL;
                            filler = Blocks.STONE;
                            depth = 2;
                        }
                    }
                    // ---- Flat areas: depends on altitude ----
                    else if (isFlat) {
                        if (highAlpine) {
                            // Very high flat areas: mostly snow, some exposed rock
                            if (mountainHash < 40) {
                                top = Blocks.STONE;
                                filler = Blocks.STONE;
                                depth = 1;
                            } else {
                                top = Blocks.SNOW;
                                filler = Blocks.STONE;
                                depth = 1;
                            }
                        } else if (aboveSnowLine) {
                            // Above snow line: snow on flat areas
                            if (mountainHash < 25) {
                                // Occasional exposed rock
                                top = Blocks.STONE;
                                filler = Blocks.STONE;
                                depth = 1;
                            } else if (mountainHash < 50) {
                                // Some gravel patches
                                top = Blocks.GRAVEL;
                                filler = Blocks.STONE;
                                depth = 2;
                            } else {
                                // Mostly snow
                                top = Blocks.SNOW;
                                filler = Blocks.STONE;
                                depth = 1;
                            }
                        } else {
                            // Below snow line but in mountain zone: gravel/grass mix
                            if (mountainHash < 80) {
                                top = Blocks.GRAVEL;
                                filler = Blocks.STONE;
                                depth = 2;
                            } else if (mountainHash < 140) {
                                top = Blocks.STONE;
                                filler = Blocks.STONE;
                                depth = 1;
                            }
                            // else keep biome default (grass)
                        }
                    }
                    // ---- Gentle slopes: transitional ----
                    else {
                        if (aboveSnowLine) {
                            // Mix of snow and rock on gentle slopes
                            if (mountainHash < 60) {
                                top = Blocks.STONE;
                                filler = Blocks.STONE;
                                depth = 1;
                            } else if (mountainHash < 100) {
                                top = Blocks.GRAVEL;
                                filler = Blocks.STONE;
                                depth = 2;
                            } else {
                                top = Blocks.SNOW;
                                filler = Blocks.STONE;
                                depth = 1;
                            }
                        } else {
                            // Below snow line: rocky terrain
                            if (mountainHash < 100) {
                                top = Blocks.STONE;
                                filler = Blocks.STONE;
                                depth = 1;
                            } else if (mountainHash < 180) {
                                top = Blocks.GRAVEL;
                                filler = Blocks.STONE;
                                depth = 2;
                            }
                            // else keep biome default
                        }
                    }
                }

                // ---- Climate biomes adjustments (only for land not covered by beach or mountain) ----
                if (!isOceanBiome && !isBeachTerrain && !isMountainTerrain) {
                    // Desert: sand + sandstone under it
                    boolean isDesert = (biome == EngineConfig.BIOME_DESERT);
                    if (isDesert) {
                        top = Blocks.DESERT_SAND;
                        filler = Blocks.DESERT_SAND;
                        depth = Math.max(depth, 6);
                    }

                    // Swamp: mix of grass, clay, and gravel patches at low elevation
                    boolean isSwamp = (biome == EngineConfig.BIOME_SWAMP);
                    if (isSwamp) {
                        int swampHash = GlobalTerrainUtils.hash8(wx + 12345, wz + 67890);
                        if (swampHash < 80) {
                            // ~31% clay patches
                            top = Blocks.CLAY;
                            filler = Blocks.CLAY;
                            depth = Math.max(depth, 3);
                        } else if (swampHash < 120) {
                            // ~16% gravel patches
                            top = Blocks.GRAVEL;
                            filler = Blocks.DIRT;
                            depth = Math.max(depth, 3);
                        }
                        // else keep default grass/dirt from surface rules
                    }

                    // Underwater areas in non-ocean biomes (lakes, rivers)
                    boolean underwater = hasWater && surfaceY < waterLevel;
                    if (underwater) {
                        int h = GlobalTerrainUtils.hash8(wx, wz);
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
                }

                // Rivers: the valley is already carved into the heightmap by
                // TerrainColumnSampler (water always at sea level). The carve
                // mask only marks channel columns for riverbed material below.
                int carvedSurfaceY = surfaceY;

                // Now fill column MIN_Y..MAX_Y
                for (int wy = EngineConfig.MIN_Y; wy <= EngineConfig.MAX_Y; wy++) {
                    short id;

                    // Bedrock base
                    if (wy < EngineConfig.MIN_Y + EngineConfig.BEDROCK_THICKNESS) {
                        id = Blocks.BEDROCK;
                        chunk.setBlock(lx, wy, lz, id);
                        continue;
                    }

                    // Above surface => air / water (using WaterLayer)
                    if (wy > carvedSurfaceY) {
                        if (hasWater && wy <= waterLevel) {
                            // Freeze surface water in snow biome
                            if ((biome == EngineConfig.BIOME_SNOW) && wy == waterLevel) {
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
                        id = Blocks.GRAVEL;
                        chunk.setBlock(lx, wy, lz, id);
                        continue;
                    }

                    // Top / filler / stone strata
                    if (wy == carvedSurfaceY) {
                        // Mountain terrain already has proper snow/stone distribution
                        // Only apply snow biome override for non-mountain areas
                        if (!isMountainTerrain && biome == EngineConfig.BIOME_SNOW && carvedSurfaceY > sea + 8) {
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

        // ---- 3) Caves (worm carvers; before structures so entrance rims are final) ----
        CaveCarver.carve(seed, chunk, layers);

        // ---- 4) Structures placement (once, after terrain fill) ----
        for (StructureMap.Placement p : localPlacements) {
            applyPlacement(chunk, layers, cx, cz, p);
        }

        return chunk;
    }

    // -------------------------------------------------------------------------
    // Placements
    // -------------------------------------------------------------------------

    private List<StructureMap.Placement> filterPlacementsForChunk(StructureMap map, int cx, int cz) {
        final int haloBlocks = EngineConfig.STRUCTURE_PLACEMENT_HALO_CHUNKS * EngineConfig.CHUNK_SIZE;

        final int chunkMinX = cx * EngineConfig.CHUNK_SIZE;
        final int chunkMinZ = cz * EngineConfig.CHUNK_SIZE;
        final int chunkMaxX = chunkMinX + (EngineConfig.CHUNK_SIZE - 1);
        final int chunkMaxZ = chunkMinZ + (EngineConfig.CHUNK_SIZE - 1);

        final int minX = chunkMinX - haloBlocks;
        final int minZ = chunkMinZ - haloBlocks;
        final int maxX = chunkMaxX + haloBlocks;
        final int maxZ = chunkMaxZ + haloBlocks;

        List<StructureMap.Placement> out = new ArrayList<>();
        for (StructureMap.Placement p : map.placements()) {
            int wx = p.wx();
            int wz = p.wz();
            if (wx >= minX && wx <= maxX && wz >= minZ && wz <= maxZ) {
                out.add(p);
            }
        }
        return out;
    }

    private void applyPlacement(Chunk chunk, RegionLayers layers, int cx, int cz, StructureMap.Placement p) {
        final int wx = p.wx();
        final int wz = p.wz();
        final int wy = p.wy();

        if (wy < EngineConfig.MIN_Y || wy > EngineConfig.MAX_Y) return;

        final short marker = p.structureId();

        // Don’t spawn structures in carved river columns (prevents weird floating / river cuts)
        if (layers.carveMask().isCarvedColumn(wx, wz)) return;

        // Determine ground top at (wx,wz) using the same overrides as terrain fill
        final int surfaceY = layers.heightmap().heightAt(wx, wz);
        final short biome = layers.biomeMap().biomeIdAtUnchecked(wx, wz);
        final short groundTop = resolveTopForStructures(layers, wx, wz, surfaceY, biome);

        // Base must be just above the column surface (StructureBuilder uses surfaceY+1)
        // Keep it lenient: allow if it’s above surface by 1..2 (small safety for future tweaks)
        if (wy <= surfaceY) return;
        if (wy > surfaceY + 2) return;

        // ----- Trees -----
        if (marker == Blocks.STRUCT_OAK_TREE) {
            if (!Blocks.isSoil(groundTop)) return;
            int h = 4 + (GlobalTerrainUtils.hash8(wx, wz) % 3);
            placeOakTreePart(chunk, cx, cz, wx, wy, wz, h);
            return;
        }

        if (marker == Blocks.STRUCT_ACACIA_TREE) {
            if (!Blocks.isSoil(groundTop)) return;
            int h = 4 + (GlobalTerrainUtils.hash8(wx, wz) % 3);
            placeAcaciaTreePart(chunk, cx, cz, wx, wy, wz, h);
            return;
        }

        if (marker == Blocks.STRUCT_JUNGLE_TREE) {
            if (!Blocks.isSoil(groundTop)) return;
            int h = 7 + (GlobalTerrainUtils.hash8(wx, wz) % 6);
            placeJungleTreePart(chunk, cx, cz, wx, wy, wz, h);
            return;
        }

        if (marker == Blocks.STRUCT_MEGA_JUNGLE) {
            if (!Blocks.isSoil(groundTop)) return;
            int h = 15 + (GlobalTerrainUtils.hash8(wx, wz) % 9);
            placeMegaJunglePartNormal(chunk, cx, cz, wx, wy, wz, h);
            return;
        }

        if (marker == Blocks.STRUCT_SPRUCE_TREE) {
            if (!Blocks.isSoil(groundTop)) return;
            int h = 8 + (GlobalTerrainUtils.hash8(wx, wz) % 6);
            placeSpruceTree(chunk, cx, cz, wx, wy, wz, h);
            return;
        }

        if (marker == Blocks.STRUCT_SNOW_TREE) {
            if (!Blocks.isSoil(groundTop)) return;
            int h = 6 + (GlobalTerrainUtils.hash8(wx, wz) % 4);
            placeSnowTree(chunk, cx, cz, wx, wy, wz, h);
            return;
        }

        // ----- Plants (single-block) -----
        if (Blocks.isVegetation(marker)) {
            if (!Blocks.isGrassLike(groundTop) && !Blocks.isSoil(groundTop)) return;

            // Place ONLY if that world position is inside THIS chunk, and only
            // if the ground survived cave carving (no flowers floating in
            // entrance holes)
            if (!isGroundedInChunk(chunk, cx, cz, wx, wy, wz)) return;
            setIfInThisChunkIfReplaceable(chunk, cx, cz, wx, wy, wz, marker);
            return;
        }

        // ----- Cactus (multi-block) -----
        if (marker == Blocks.CACTUS) {
            if (!Blocks.isSandLike(groundTop)) return;

            if (!isGroundedInChunk(chunk, cx, cz, wx, wy, wz)) return;
            int height = 2 + (GlobalTerrainUtils.hash8(wx, wz) % 3);
            for (int dy = 0; dy < height; dy++) {
                int y = wy + dy;
                if (y > EngineConfig.MAX_Y) break;
                setIfInThisChunkIfReplaceable(chunk, cx, cz, wx, y, wz, Blocks.CACTUS);
            }
            return;
        }

        // Fallback: treat as direct block id at (wx,wy,wz), but only if it belongs to this chunk
        setIfInThisChunkIfReplaceable(chunk, cx, cz, wx, wy, wz, marker);
    }

    private short resolveTopForStructures(RegionLayers layers, int wx, int wz, int surfaceY, short biome) {
        final int sea = EngineConfig.SEA_LEVEL;

        short top = layers.surfaceRules().topBlockAt(wx, wz);

        // Same overrides as terrain fill
        boolean isBeach = Math.abs(surfaceY - sea) <= EngineConfig.BEACH_BAND;
        if (isBeach && surfaceY >= sea - 2 && surfaceY <= sea + 6) {
            return Blocks.SAND;
        }

        boolean isDesert = (biome == EngineConfig.BIOME_DESERT);
        if (isDesert && !isBeach) {
            return Blocks.DESERT_SAND;
        }

        boolean underwater = surfaceY < sea;
        if (underwater) {
            int h = GlobalTerrainUtils.hash8(wx, wz);
            if (h < EngineConfig.OCEAN_CLAY_CHANCE_PER_256) return Blocks.CLAY;
            if (h < EngineConfig.OCEAN_CLAY_CHANCE_PER_256 + EngineConfig.OCEAN_GRAVEL_CHANCE_PER_256) return Blocks.GRAVEL;
            return Blocks.SAND;
        }

        return top;
    }

    /**
     * True when (wx, wy, wz) is inside this chunk AND the block directly below
     * is still solid ground (cave carving may have removed it).
     */
    private boolean isGroundedInChunk(Chunk chunk, int cx, int cz, int wx, int wy, int wz) {
        if (!isInChunk(wx, wz, cx, cz)) return false;
        if (wy - 1 < EngineConfig.MIN_Y) return false;

        int lx = Math.floorMod(wx, EngineConfig.CHUNK_SIZE);
        int lz = Math.floorMod(wz, EngineConfig.CHUNK_SIZE);

        short below = chunk.getBlock(lx, wy - 1, lz);
        return below != Blocks.AIR && below != Blocks.WATER;
    }

    private void setIfInThisChunkIfReplaceable(Chunk chunk, int cx, int cz, int wx, int wy, int wz, short id) {
        if (wy < EngineConfig.MIN_Y || wy > EngineConfig.MAX_Y) return;
        if (!isInChunk(wx, wz, cx, cz)) return;

        int lx = Math.floorMod(wx, EngineConfig.CHUNK_SIZE);
        int lz = Math.floorMod(wz, EngineConfig.CHUNK_SIZE);

        short cur = chunk.getBlock(lx, wy, lz);
        // Keep it simple: only place into air (prevents weird overwrites across overlapping placements)
        if (cur == Blocks.AIR) {
            chunk.setBlock(lx, wy, lz, id);
        }
    }

    private void setLeavesIfAir(Chunk chunk, int cx, int cz, int wx, int wy, int wz, short leavesId) {
        if (wy < EngineConfig.MIN_Y || wy > EngineConfig.MAX_Y) return;
        if (!isInChunk(wx, wz, cx, cz)) return;

        int lx = Math.floorMod(wx, EngineConfig.CHUNK_SIZE);
        int lz = Math.floorMod(wz, EngineConfig.CHUNK_SIZE);

        if (chunk.getBlock(lx, wy, lz) == Blocks.AIR) {
            chunk.setBlock(lx, wy, lz, leavesId);
        }
    }

    private void setIfInThisChunk(Chunk chunk, int cx, int cz, int wx, int wy, int wz, short id) {
        if (wy < EngineConfig.MIN_Y || wy > EngineConfig.MAX_Y) return;
        if (!isInChunk(wx, wz, cx, cz)) return;

        int lx = Math.floorMod(wx, EngineConfig.CHUNK_SIZE);
        int lz = Math.floorMod(wz, EngineConfig.CHUNK_SIZE);

        chunk.setBlock(lx, wy, lz, id);
    }

    private boolean isInChunk(int wx, int wz, int cx, int cz) {
        return Math.floorDiv(wx, EngineConfig.CHUNK_SIZE) == cx && Math.floorDiv(wz, EngineConfig.CHUNK_SIZE) == cz;
    }

    /**
     * Compute steepness at a position by checking height differences with neighbors.
     * Used for mountain terrain to determine cliff faces (stone) vs flatter areas (snow/gravel).
     */
    private int computeSteepness(RegionLayers layers, int wx, int wz, int centerHeight) {
        int maxDiff = 0;

        // Check 4 cardinal neighbors
        int[] dx = {-2, 2, 0, 0};
        int[] dz = {0, 0, -2, 2};

        for (int i = 0; i < 4; i++) {
            int nx = wx + dx[i];
            int nz = wz + dz[i];

            // Get neighbor height from heightmap
            int neighborHeight = layers.heightmap().heightAt(nx, nz);
            int diff = Math.abs(centerHeight - neighborHeight);
            if (diff > maxDiff) {
                maxDiff = diff;
            }
        }

        return maxDiff;
    }

    /**
     * Places only the part of the oak tree that belongs to THIS chunk.
     * This allows trees near borders to be placed consistently when neighbor chunks build.
     */
    private void placeOakTreePart(Chunk chunk, int cx, int cz, int wxBase, int wyBase, int wzBase, int trunkH) {
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
                setLeavesIfAir(chunk, cx, cz, wxBase + dx, crownY,     wzBase + dz, Blocks.OAK_LEAVES);
                setLeavesIfAir(chunk, cx, cz, wxBase + dx, crownY + 1, wzBase + dz, Blocks.OAK_LEAVES);

                // top cap (smaller)
                if (dist2 <= 2) {
                    setLeavesIfAir(chunk, cx, cz, wxBase + dx, crownY + 2, wzBase + dz, Blocks.OAK_LEAVES);
                }
            }
        }
    }

    private void placeAcaciaTreePart(Chunk chunk, int cx, int cz, int wxBase, int wyBase, int wzBase, int trunkH) {
        int dir = GlobalTerrainUtils.hash8(wxBase, wzBase) & 3; // 0..3
        int bendStart = trunkH - 2;

        // trunk straight then slight bend
        int wx = wxBase;
        int wz = wzBase;

        for (int dy = 0; dy < trunkH; dy++) {
            if (dy >= bendStart) {
                switch (dir) {
                    case 0 -> wx++;
                    case 1 -> wx--;
                    case 2 -> wz++;
                    default -> wz--;
                }
            }
            setIfInThisChunk(chunk, cx, cz, wx, wyBase + dy, wz, Blocks.ACACIA_LOG);
        }

        // flat-ish canopy
        int crownY = wyBase + trunkH;
        for (int dz = -2; dz <= 2; dz++) {
            for (int dx = -2; dx <= 2; dx++) {
                int dist2 = dx * dx + dz * dz;
                if (dist2 > 5) continue;
                setLeavesIfAir(chunk, cx, cz, wx + dx, crownY, wz + dz, Blocks.ACACIA_LEAVES);
                if (dist2 <= 2) setLeavesIfAir(chunk, cx, cz, wx + dx, crownY + 1, wz + dz, Blocks.ACACIA_LEAVES);
            }
        }
    }

    private void placeJungleTreePart(Chunk chunk, int cx, int cz, int wxBase, int wyBase, int wzBase, int trunkH) {
        // tall straight trunk
        for (int dy = 0; dy < trunkH; dy++) {
            setIfInThisChunk(chunk, cx, cz, wxBase, wyBase + dy, wzBase, Blocks.JUNGLE_LOG);
        }

        // leafy crown (bigger than oak)
        int crownY = wyBase + trunkH - 1;
        for (int dz = -3; dz <= 3; dz++) {
            for (int dx = -3; dx <= 3; dx++) {
                int dist2 = dx * dx + dz * dz;
                if (dist2 > 10) continue;
                setLeavesIfAir(chunk, cx, cz, wxBase + dx, crownY, wzBase + dz, Blocks.JUNGLE_LEAVES);
                setLeavesIfAir(chunk, cx, cz, wxBase + dx, crownY + 1, wzBase + dz, Blocks.JUNGLE_LEAVES);
                if (dist2 <= 4) setLeavesIfAir(chunk, cx, cz, wxBase + dx, crownY + 2, wzBase + dz, Blocks.JUNGLE_LEAVES);
            }
        }
    }

    private void placeMegaJunglePartNormal(Chunk chunk, int cx, int cz, int wxBase, int wyBase, int wzBase, int trunkH) {
        // 2x2 trunk (anchor at wxBase,wzBase)
        for (int dy = 0; dy < trunkH; dy++) {
            setIfInThisChunk(chunk, cx, cz, wxBase,     wyBase + dy, wzBase,     Blocks.JUNGLE_LOG);
            setIfInThisChunk(chunk, cx, cz, wxBase + 1, wyBase + dy, wzBase,     Blocks.JUNGLE_LOG);
            setIfInThisChunk(chunk, cx, cz, wxBase,     wyBase + dy, wzBase + 1, Blocks.JUNGLE_LOG);
            setIfInThisChunk(chunk, cx, cz, wxBase + 1, wyBase + dy, wzBase + 1, Blocks.JUNGLE_LOG);
        }

        // huge canopy
        int crownY = wyBase + trunkH;
        for (int dz = -5; dz <= 5; dz++) {
            for (int dx = -5; dx <= 5; dx++) {
                int dist2 = dx * dx + dz * dz;
                if (dist2 > 24) continue;
                setLeavesIfAir(chunk, cx, cz, wxBase + dx, crownY,     wzBase + dz, Blocks.JUNGLE_LEAVES);
                setLeavesIfAir(chunk, cx, cz, wxBase + dx, crownY + 1, wzBase + dz, Blocks.JUNGLE_LEAVES);
                if (dist2 <= 12) setLeavesIfAir(chunk, cx, cz, wxBase + dx, crownY + 2, wzBase + dz, Blocks.JUNGLE_LEAVES);
            }
        }
    }

    private void placeSpruceTree(
            Chunk chunk,
            int cx, int cz,
            int wxBase, int wyBase, int wzBase,
            int trunkH
    ) {
        // Trunk
        for (int dy = 0; dy < trunkH; dy++) {
            setIfInThisChunk(chunk, cx, cz, wxBase, wyBase + dy, wzBase, Blocks.SPRUCE_LOG);
        }

        int topY = wyBase + trunkH - 1;

        // 4 leaves around the top log (perfectly symmetric)
        setLeavesIfAir(chunk, cx, cz, wxBase + 1, topY, wzBase,     Blocks.SPRUCE_LEAVES);
        setLeavesIfAir(chunk, cx, cz, wxBase - 1, topY, wzBase,     Blocks.SPRUCE_LEAVES);
        setLeavesIfAir(chunk, cx, cz, wxBase,     topY, wzBase + 1, Blocks.SPRUCE_LEAVES);
        setLeavesIfAir(chunk, cx, cz, wxBase,     topY, wzBase - 1, Blocks.SPRUCE_LEAVES);

        // Optional tip
        setLeavesIfAir(chunk, cx, cz, wxBase, topY + 1, wzBase, Blocks.SPRUCE_LEAVES);

        // Thin pyramid foliage (radius never exceeds 3)
        int foliageBottomY = wyBase + Math.max(2, trunkH / 3);

        int layer = 0;
        for (int y = topY - 1; y >= foliageBottomY; y--, layer++) {
            int t = (topY - 1) - y;      // 0..down from just under top

            // Grow slowly: 1,1,2,2,3,3,3,3...
            int radius = 1 + (t / 2);
            if (radius > 3) radius = 3;

            // (Optional) make it a bit thinner sometimes while staying symmetric
            if (layer % 4 == 3 && radius > 1) radius--;

            placeSpruceLayerSymmetric(chunk, cx, cz, wxBase, y, wzBase, radius, Blocks.SPRUCE_LEAVES);
        }
    }

    /**
     * Perfectly mirrored layer using symmetric masks, max radius = 3.
     * This prevents "one side thicker than the other".
     */
    private void placeSpruceLayerSymmetric(
            Chunk chunk,
            int cx, int cz,
            int wxC, int wy, int wzC,
            int radius,
            short leavesId
    ) {
        // Masks are offsets (dx,dz). All are explicitly symmetric.
        // radius 1 => plus + center (no diagonals) => thin look
        // radius 2 => diamond-ish ring (still trimmed corners)
        // radius 3 => fuller but still capped and symmetric
        final int[][] offsets;
        switch (radius) {
            case 1 -> offsets = new int[][]{
                    {0,0},
                    {1,0},{-1,0},{0,1},{0,-1}
            };
            case 2 -> offsets = new int[][]{
                    {0,0},
                    {1,0},{-1,0},{0,1},{0,-1},
                    {2,0},{-2,0},{0,2},{0,-2},
                    {1,1},{1,-1},{-1,1},{-1,-1}
            };
            default -> offsets = new int[][]{
                    {0,0},
                    {1,0},{-1,0},{0,1},{0,-1},
                    {2,0},{-2,0},{0,2},{0,-2},
                    {3,0},{-3,0},{0,3},{0,-3},
                    {1,1},{1,-1},{-1,1},{-1,-1},
                    {2,1},{2,-1},{-2,1},{-2,-1},
                    {1,2},{-1,2},{1,-2},{-1,-2}
            };
        }

        for (int[] o : offsets) {
            int dx = o[0];
            int dz = o[1];
            setLeavesIfAir(chunk, cx, cz, wxC + dx, wy, wzC + dz, leavesId);
        }
    }

    /**
     * Places a snow tree - similar to oak but with snow leaves.
     */
    private void placeSnowTree(Chunk chunk, int cx, int cz, int wxBase, int wyBase, int wzBase, int trunkH) {
        // Trunk (using snow log)
        for (int dy = 0; dy < trunkH; dy++) {
            setIfInThisChunk(chunk, cx, cz, wxBase, wyBase + dy, wzBase, Blocks.SNOW_LOG);
        }

        // Leaves blob (similar to oak but with snow leaves)
        int crownY = wyBase + trunkH - 1;
        for (int dz = -2; dz <= 2; dz++) {
            for (int dx = -2; dx <= 2; dx++) {
                int dist2 = dx * dx + dz * dz;
                if (dist2 > 6) continue;

                // two layers of leaves
                setLeavesIfAir(chunk, cx, cz, wxBase + dx, crownY,     wzBase + dz, Blocks.SNOW_LEAVES);
                setLeavesIfAir(chunk, cx, cz, wxBase + dx, crownY + 1, wzBase + dz, Blocks.SNOW_LEAVES);

                // top cap (smaller)
                if (dist2 <= 2) {
                    setLeavesIfAir(chunk, cx, cz, wxBase + dx, crownY + 2, wzBase + dz, Blocks.SNOW_LEAVES);
                }
            }
        }
    }
}

