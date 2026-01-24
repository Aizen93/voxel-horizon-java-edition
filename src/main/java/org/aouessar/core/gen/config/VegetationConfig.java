package org.aouessar.core.gen.config;

import java.util.List;

/**
 * Configuration for vegetation placement in a biome.
 */
public record VegetationConfig(
    List<Short> types,
    float density,
    DistributionType distribution,
    ClusteringConfig clustering
) {
    public static final VegetationConfig EMPTY = new VegetationConfig(
        List.of(), 0.0f, DistributionType.NONE, ClusteringConfig.DISABLED
    );

    /**
     * Check if vegetation placement is enabled for this biome.
     */
    public boolean hasVegetation() {
        return !types.isEmpty() && density > 0.0f && distribution != DistributionType.NONE;
    }
}
