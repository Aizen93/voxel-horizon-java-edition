package org.aouessar.core.gen.config;

import java.util.List;

/**
 * Configuration for tree placement in a biome.
 */
public record TreesConfig(
    List<TreeType> types,
    float density,
    DistributionType distribution,
    ClusteringConfig clustering,
    ClearingsConfig clearings
) {
    public static final TreesConfig EMPTY = new TreesConfig(
        List.of(), 0.0f, DistributionType.NONE,
        ClusteringConfig.DISABLED, ClearingsConfig.DISABLED
    );

    /**
     * Check if tree placement is enabled for this biome.
     */
    public boolean hasTreePlacement() {
        return !types.isEmpty() && density > 0.0f && distribution != DistributionType.NONE;
    }
}
