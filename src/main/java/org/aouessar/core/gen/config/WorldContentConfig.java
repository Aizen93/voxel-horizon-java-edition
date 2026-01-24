package org.aouessar.core.gen.config;

import java.util.Map;

/**
 * Complete world content configuration loaded from JSON.
 * Immutable and thread-safe.
 */
public record WorldContentConfig(
    String version,
    Map<String, BiomeConfig> biomes,
    Map<String, String> globalBlocks
) {
    public WorldContentConfig {
        // Make defensive copies to ensure immutability
        biomes = Map.copyOf(biomes);
        globalBlocks = Map.copyOf(globalBlocks);
    }

    /**
     * Get biome configuration by name.
     * @param biomeName the biome name (e.g., "PLAINS", "DESERT")
     * @return the BiomeConfig or null if not found
     */
    public BiomeConfig getBiome(String biomeName) {
        return biomes.get(biomeName);
    }
}
