package org.aouessar.core.gen.impl;

import org.aouessar.core.gen.StructureBuilder;
import org.aouessar.core.gen.config.BiomeConfig;
import org.aouessar.core.gen.config.TreeType;
import org.aouessar.core.gen.config.TreesConfig;
import org.aouessar.core.gen.config.VegetationConfig;
import org.aouessar.core.gen.config.WorldContentConfig;
import org.aouessar.core.gen.config.WorldContentLoader;
import org.aouessar.core.math.GlobalTerrainUtils;
import org.aouessar.core.world.layers.BiomeMap;
import org.aouessar.core.world.layers.Heightmap;
import org.aouessar.core.world.layers.LayerRect;
import org.aouessar.core.world.layers.StructureMap;
import org.aouessar.shared.EngineConfig;

import java.util.ArrayList;
import java.util.List;

public final class DefaultStructureBuilder implements StructureBuilder {

    private static final int TREES_TRIES_PER_CHUNK   = 6;
    private static final int PLANTS_TRIES_PER_CHUNK  = 24;

    // Static tree/vegetation data loaded from JSON at class initialization
    private static final short[][] BIOME_TREE_MARKERS = new short[16][];
    private static final int[] BIOME_TREE_DENSITY = new int[16];
    private static final short[][] BIOME_VEG_TYPES = new short[16][];
    private static final int[] BIOME_VEG_DENSITY = new int[16];

    static {
        // Set defaults (empty arrays)
        for (int i = 0; i < 16; i++) {
            BIOME_TREE_MARKERS[i] = new short[0];
            BIOME_VEG_TYPES[i] = new short[0];
        }

        // Load from JSON config
        try {
            WorldContentConfig config = WorldContentLoader.load();
            loadBiomeStructures(config, EngineConfig.BIOME_PLAINS, "PLAINS");
            loadBiomeStructures(config, EngineConfig.BIOME_DESERT, "DESERT");
            loadBiomeStructures(config, EngineConfig.BIOME_SNOW, "SNOW");
            loadBiomeStructures(config, EngineConfig.BIOME_FOREST, "FOREST");
            loadBiomeStructures(config, EngineConfig.BIOME_SAVANNA, "SAVANNA");
            loadBiomeStructures(config, EngineConfig.BIOME_SWAMP, "SWAMP");
            loadBiomeStructures(config, EngineConfig.BIOME_JUNGLE, "JUNGLE");
        } catch (Exception e) {
            System.err.println("Failed to load world content config for structures, using defaults: " + e.getMessage());
        }
    }

    private static void loadBiomeStructures(WorldContentConfig config, short biomeId, String biomeName) {
        BiomeConfig bc = config.getBiome(biomeName);
        if (bc == null) return;

        // Trees
        TreesConfig tc = bc.structures().trees();
        if (tc.hasTreePlacement()) {
            List<TreeType> types = tc.types();
            short[] markers = new short[types.size()];
            for (int i = 0; i < types.size(); i++) {
                markers[i] = types.get(i).getStructureMarkerId();
            }
            BIOME_TREE_MARKERS[biomeId] = markers;
            BIOME_TREE_DENSITY[biomeId] = (int) (tc.density() * 255);
        }

        // Vegetation
        VegetationConfig vc = bc.structures().vegetation();
        if (vc.hasVegetation()) {
            List<Short> types = vc.types();
            short[] veg = new short[types.size()];
            for (int i = 0; i < types.size(); i++) {
                veg[i] = types.get(i);
            }
            BIOME_VEG_TYPES[biomeId] = veg;
            BIOME_VEG_DENSITY[biomeId] = (int) (vc.density() * 255);
        }
    }

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

                // ----- Vegetation (from JSON config) -----
                for (int t = 0; t < PLANTS_TRIES_PER_CHUNK; t++) {
                    long h = GlobalTerrainUtils.hash(seed, cx, cz, 0x30_0000 + t);

                    int wx = cx * EngineConfig.CHUNK_SIZE + (int) (h & 15);
                    int wz = cz * EngineConfig.CHUNK_SIZE + (int) ((h >>> 4) & 15);
                    if (!rect.contains(wx, wz)) continue;

                    int surfaceY = heightmap.heightAt(wx, wz);
                    if (surfaceY <= EngineConfig.SEA_LEVEL) continue;

                    short biome = biomeMap.biomeIdAtUnchecked(wx, wz);

                    short plant = getVegetation(h, biome);
                    if (plant == 0) continue;

                    placements.add(new StructureMap.Placement(wx, surfaceY + 1, wz, plant));
                }
            }
        }

        return new StructureMap(rect, placements);
    }

    private static short getMarker(long h, short biome) {
        short[] markers = BIOME_TREE_MARKERS[biome];
        if (markers.length == 0) return 0;

        int r = (int) ((h >>> 12) & 0xFF);
        if (r > BIOME_TREE_DENSITY[biome]) return 0;

        // Select tree type (support multiple types per biome)
        int idx = (int) ((h >>> 20) % markers.length);
        return markers[idx];
    }

    private static short getVegetation(long h, short biome) {
        short[] types = BIOME_VEG_TYPES[biome];
        if (types.length == 0) return 0;

        int r = (int) ((h >>> 12) & 0xFF);
        if (r > BIOME_VEG_DENSITY[biome]) return 0;

        // Select vegetation type from JSON config
        int idx = (int) ((h >>> 20) % types.length);
        return types[idx];
    }
}