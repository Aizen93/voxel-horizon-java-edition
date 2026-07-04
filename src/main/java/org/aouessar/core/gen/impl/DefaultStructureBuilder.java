package org.aouessar.core.gen.impl;

import org.aouessar.core.gen.StructureBuilder;
import org.aouessar.core.gen.config.BiomeConfig;
import org.aouessar.core.gen.config.ClearingsConfig;
import org.aouessar.core.gen.config.ClusteringConfig;
import org.aouessar.core.gen.config.DistributionType;
import org.aouessar.core.gen.config.TreeType;
import org.aouessar.core.gen.config.TreesConfig;
import org.aouessar.core.gen.config.VegetationConfig;
import org.aouessar.core.gen.config.WorldContentConfig;
import org.aouessar.core.gen.config.WorldContentLoader;
import org.aouessar.core.math.GlobalTerrainUtils;
import org.aouessar.core.noise.FastNoiseLite;
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
    private static final DistributionType[] BIOME_TREE_DIST = new DistributionType[16];
    private static final ClusteringConfig[] BIOME_TREE_CLUSTER = new ClusteringConfig[16];
    private static final ClearingsConfig[] BIOME_TREE_CLEARINGS = new ClearingsConfig[16];
    private static final short[][] BIOME_VEG_TYPES = new short[16][];
    private static final int[] BIOME_VEG_DENSITY = new int[16];

    static {
        // Set defaults (empty arrays)
        for (int i = 0; i < 16; i++) {
            BIOME_TREE_MARKERS[i] = new short[0];
            BIOME_VEG_TYPES[i] = new short[0];
            BIOME_TREE_DIST[i] = DistributionType.SCATTERED;
            BIOME_TREE_CLUSTER[i] = ClusteringConfig.DISABLED;
            BIOME_TREE_CLEARINGS[i] = ClearingsConfig.DISABLED;
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
            BIOME_TREE_DIST[biomeId] = tc.distribution();
            BIOME_TREE_CLUSTER[biomeId] = tc.clustering();
            BIOME_TREE_CLEARINGS[biomeId] = tc.clearings();
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

        // Coherent noise fields for distribution-aware placement (PATCHY /
        // CLUSTERED groves and tree-free clearings). Created per call (cheap)
        // and seeded from the world seed, so the padded region overlap
        // generates identical groves on both sides of a region border.
        FastNoiseLite groveN = new FastNoiseLite(GlobalTerrainUtils.mixSeed(seed, 0x0670BE5A));
        groveN.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        groveN.SetFrequency(1.0f / 64.0f);

        FastNoiseLite clearingN = new FastNoiseLite(GlobalTerrainUtils.mixSeed(seed, 0x0C1EA716));
        clearingN.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        clearingN.SetFrequency(1.0f / 48.0f);

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

                    // Skip geographic biomes that shouldn't have trees
                    if (isNoStructureBiome(biome)) continue;

                    float densityFactor = treeDensityFactor(biome, wx, wz, groveN, clearingN);
                    short marker = getMarker(h, biome, densityFactor);

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

                    // Skip geographic biomes that shouldn't have vegetation
                    if (isNoStructureBiome(biome)) continue;

                    short plant = getVegetation(h, biome);
                    if (plant == 0) continue;

                    placements.add(new StructureMap.Placement(wx, surfaceY + 1, wz, plant));
                }
            }
        }

        return new StructureMap(rect, placements);
    }

    /**
     * Check if this biome should not have any structures (trees/vegetation).
     * Only ocean biomes are excluded - beach is a terrain feature handled by ChunkBuilder.
     */
    private static boolean isNoStructureBiome(short biome) {
        return biome == EngineConfig.BIOME_OCEAN
            || biome == EngineConfig.BIOME_DEEP_OCEAN;
    }

    /**
     * Local tree density multiplier from the biome's configured distribution:
     * <ul>
     *   <li>UNIFORM / SCATTERED: 1.0 everywhere (classic random scatter)</li>
     *   <li>PATCHY: soft irregular patches — sparse gaps, denser thickets</li>
     *   <li>CLUSTERED: tight groves — mostly empty ground with boosted-density
     *       clumps sized by the clustering config</li>
     * </ul>
     * Clearings (when enabled) carve tree-free meadows on top of any pattern;
     * vegetation is deliberately unaffected, so clearings still get flowers.
     */
    private static float treeDensityFactor(
            short biome, int wx, int wz,
            FastNoiseLite groveN, FastNoiseLite clearingN
    ) {
        DistributionType dist = BIOME_TREE_DIST[biome];
        float factor = 1f;

        if (dist == DistributionType.PATCHY || dist == DistributionType.CLUSTERED) {
            ClusteringConfig cc = BIOME_TREE_CLUSTER[biome];
            int maxSize = (cc != null && cc.enabled()) ? cc.maxSize() : 8;
            // Bigger configured clusters => larger grove features
            float s = 64f / (16f + 4f * maxSize);
            float grove01 = GlobalTerrainUtils.to01(groveN.GetNoise(wx * s, wz * s));

            if (dist == DistributionType.CLUSTERED) {
                factor = GlobalTerrainUtils.smoothstep(0.52f, 0.70f, grove01) * 2.2f;
            } else {
                factor = GlobalTerrainUtils.smoothstep(0.40f, 0.62f, grove01) * 1.5f;
            }
        }

        ClearingsConfig clc = BIOME_TREE_CLEARINGS[biome];
        if (clc != null && clc.enabled() && factor > 0f) {
            float s = 48f / (6f * Math.max(4, clc.averageRadius()));
            float clear01 = GlobalTerrainUtils.to01(clearingN.GetNoise(wx * s + 31000f, wz * s - 17000f));
            factor *= 1f - GlobalTerrainUtils.smoothstep(0.60f, 0.72f, clear01);
        }

        return factor;
    }

    private static short getMarker(long h, short biome, float densityFactor) {
        short[] markers = BIOME_TREE_MARKERS[biome];
        if (markers.length == 0) return 0;

        int effectiveDensity = (int) Math.min(255, BIOME_TREE_DENSITY[biome] * densityFactor);
        if (effectiveDensity <= 0) return 0;

        int r = (int) ((h >>> 12) & 0xFF);
        if (r > effectiveDensity) return 0;

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