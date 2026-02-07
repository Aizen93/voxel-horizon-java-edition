package org.aouessar.core.gen.config;

import com.google.gson.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Loads world content configuration from JSON files.
 * Supports versioned configuration files (e.g., world_content_v1.json, world_content_v2.json).
 *
 * <p>Usage:</p>
 * <pre>
 * WorldContentConfig config = WorldContentLoader.load(); // loads latest version
 * WorldContentConfig config = WorldContentLoader.load(1); // loads specific version
 * </pre>
 */
public final class WorldContentLoader {

    private static final String CONFIG_PATH_PATTERN = "constraints/world_content_v%d.json";
    private static final int LATEST_VERSION = 1; // Update when adding new versions

    private static volatile WorldContentConfig cachedConfig;

    private WorldContentLoader() {}

    /**
     * Load the latest version of world content configuration.
     * Result is cached for subsequent calls.
     *
     * @return the loaded configuration
     * @throws WorldContentLoadException if loading fails
     */
    public static WorldContentConfig load() {
        return load(LATEST_VERSION);
    }

    /**
     * Load a specific version of world content configuration.
     *
     * @param version the version number (e.g., 1 for world_content_v1.json)
     * @return the loaded configuration
     * @throws WorldContentLoadException if loading fails
     */
    public static WorldContentConfig load(int version) {
        if (version == LATEST_VERSION && cachedConfig != null) {
            return cachedConfig;
        }

        String resourcePath = String.format(CONFIG_PATH_PATTERN, version);

        try (InputStream is = WorldContentLoader.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new WorldContentLoadException("Configuration file not found: " + resourcePath);
            }

            WorldContentConfig config = parseJson(is, version);

            if (version == LATEST_VERSION) {
                cachedConfig = config;
            }

            return config;
        } catch (IOException e) {
            throw new WorldContentLoadException("Failed to read configuration file: " + resourcePath, e);
        } catch (JsonParseException e) {
            throw new WorldContentLoadException("Invalid JSON in configuration file: " + resourcePath, e);
        }
    }

    /**
     * Reload configuration, clearing the cache.
     */
    public static WorldContentConfig reload() {
        cachedConfig = null;
        return load();
    }

    /**
     * Get the latest supported version number.
     */
    public static int getLatestVersion() {
        return LATEST_VERSION;
    }

    private static WorldContentConfig parseJson(InputStream is, int expectedVersion) {
        JsonObject root = JsonParser.parseReader(new InputStreamReader(is, StandardCharsets.UTF_8))
                                    .getAsJsonObject();

        String version = root.has("version") ? root.get("version").getAsString() : "1.0.0";

        // Validate version compatibility
        int majorVersion = parseMajorVersion(version);
        if (majorVersion != expectedVersion) {
            throw new WorldContentLoadException(
                "Version mismatch: expected v" + expectedVersion + " but found " + version);
        }

        // Parse biomes
        Map<String, BiomeConfig> biomes = new LinkedHashMap<>();
        if (root.has("biomes")) {
            JsonObject biomesObj = root.getAsJsonObject("biomes");
            for (Map.Entry<String, JsonElement> entry : biomesObj.entrySet()) {
                String biomeName = entry.getKey();
                BiomeConfig biomeConfig = parseBiome(biomeName, entry.getValue().getAsJsonObject());
                biomes.put(biomeName, biomeConfig);
            }
        }

        // Parse global blocks
        Map<String, String> globalBlocks = new LinkedHashMap<>();
        if (root.has("global_blocks")) {
            JsonObject globalObj = root.getAsJsonObject("global_blocks");
            for (Map.Entry<String, JsonElement> entry : globalObj.entrySet()) {
                globalBlocks.put(entry.getKey(), entry.getValue().getAsString());
            }
        }

        return new WorldContentConfig(version, biomes, globalBlocks);
    }

    private static BiomeConfig parseBiome(String name, JsonObject obj) {
        // Parse surface blocks
        List<Short> surfaceBlocks = new ArrayList<>();
        if (obj.has("surface_blocks")) {
            JsonArray arr = obj.getAsJsonArray("surface_blocks");
            for (JsonElement el : arr) {
                String blockName = el.getAsString();
                short blockId = BlockRegistry.getBlockId(blockName);
                if (blockId >= 0) {
                    surfaceBlocks.add(blockId);
                } else {
                    System.err.println("Warning: Unknown block '" + blockName + "' in biome " + name);
                }
            }
        }

        // Parse structures
        StructuresConfig structures = StructuresConfig.EMPTY;
        if (obj.has("structures")) {
            structures = parseStructures(obj.getAsJsonObject("structures"), name);
        }

        return new BiomeConfig(name, List.copyOf(surfaceBlocks), structures);
    }

    private static StructuresConfig parseStructures(JsonObject obj, String biomeName) {
        TreesConfig trees = TreesConfig.EMPTY;
        VegetationConfig vegetation = VegetationConfig.EMPTY;

        if (obj.has("trees")) {
            trees = parseTreesConfig(obj.getAsJsonObject("trees"), biomeName);
        }

        if (obj.has("vegetation")) {
            vegetation = parseVegetationConfig(obj.getAsJsonObject("vegetation"), biomeName);
        }

        return new StructuresConfig(trees, vegetation);
    }

    private static TreesConfig parseTreesConfig(JsonObject obj, String biomeName) {
        // Parse tree types
        List<TreeType> types = new ArrayList<>();
        if (obj.has("types")) {
            JsonArray arr = obj.getAsJsonArray("types");
            for (JsonElement el : arr) {
                String typeName = el.getAsString();
                TreeType treeType = TreeType.fromString(typeName);
                if (treeType != null) {
                    types.add(treeType);
                } else {
                    System.err.println("Warning: Unknown tree type '" + typeName + "' in biome " + biomeName);
                }
            }
        }

        float density = obj.has("density") ? obj.get("density").getAsFloat() : 0.0f;

        DistributionType distribution = DistributionType.NONE;
        if (obj.has("distribution")) {
            try {
                distribution = DistributionType.valueOf(obj.get("distribution").getAsString());
            } catch (IllegalArgumentException e) {
                System.err.println("Warning: Unknown distribution type in biome " + biomeName);
            }
        }

        ClusteringConfig clustering = ClusteringConfig.DISABLED;
        if (obj.has("clustering")) {
            JsonObject clusterObj = obj.getAsJsonObject("clustering");
            boolean enabled = clusterObj.has("enabled") && clusterObj.get("enabled").getAsBoolean();
            int[] clusterSize = null;
            if (clusterObj.has("cluster_size")) {
                JsonArray sizeArr = clusterObj.getAsJsonArray("cluster_size");
                clusterSize = new int[] { sizeArr.get(0).getAsInt(), sizeArr.get(1).getAsInt() };
            }
            clustering = ClusteringConfig.of(enabled, clusterSize);
        }

        ClearingsConfig clearings = ClearingsConfig.DISABLED;
        if (obj.has("clearings")) {
            JsonObject clearObj = obj.getAsJsonObject("clearings");
            boolean enabled = clearObj.has("enabled") && clearObj.get("enabled").getAsBoolean();
            int radius = clearObj.has("average_radius") ? clearObj.get("average_radius").getAsInt() : 0;
            clearings = ClearingsConfig.of(enabled, radius);
        }

        return new TreesConfig(List.copyOf(types), density, distribution, clustering, clearings);
    }

    private static VegetationConfig parseVegetationConfig(JsonObject obj, String biomeName) {
        // Parse vegetation types (these are block IDs)
        List<Short> types = new ArrayList<>();
        if (obj.has("types")) {
            JsonArray arr = obj.getAsJsonArray("types");
            for (JsonElement el : arr) {
                String blockName = el.getAsString();
                short blockId = BlockRegistry.getBlockId(blockName);
                if (blockId >= 0) {
                    types.add(blockId);
                } else {
                    System.err.println("Warning: Unknown vegetation '" + blockName + "' in biome " + biomeName);
                }
            }
        }

        float density = obj.has("density") ? obj.get("density").getAsFloat() : 0.0f;

        DistributionType distribution = DistributionType.NONE;
        if (obj.has("distribution")) {
            try {
                distribution = DistributionType.valueOf(obj.get("distribution").getAsString());
            } catch (IllegalArgumentException e) {
                System.err.println("Warning: Unknown distribution type in biome " + biomeName);
            }
        }

        ClusteringConfig clustering = ClusteringConfig.DISABLED;
        if (obj.has("clustering")) {
            JsonObject clusterObj = obj.getAsJsonObject("clustering");
            boolean enabled = clusterObj.has("enabled") && clusterObj.get("enabled").getAsBoolean();
            int[] clusterSize = null;
            if (clusterObj.has("cluster_size")) {
                JsonArray sizeArr = clusterObj.getAsJsonArray("cluster_size");
                clusterSize = new int[] { sizeArr.get(0).getAsInt(), sizeArr.get(1).getAsInt() };
            }
            clustering = ClusteringConfig.of(enabled, clusterSize);
        }

        return new VegetationConfig(List.copyOf(types), density, distribution, clustering);
    }

    private static int parseMajorVersion(String version) {
        if (version == null || version.isBlank()) return 0;
        String[] parts = version.split("\\.");
        try {
            return Integer.parseInt(parts[0]);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Exception thrown when world content loading fails.
     */
    public static class WorldContentLoadException extends RuntimeException {
        public WorldContentLoadException(String message) {
            super(message);
        }

        public WorldContentLoadException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
