package org.aouessar.core.gen.config;

/**
 * Clustering configuration for structures.
 */
public record ClusteringConfig(
    boolean enabled,
    int minSize,
    int maxSize
) {
    public static final ClusteringConfig DISABLED = new ClusteringConfig(false, 0, 0);

    /**
     * Creates a clustering config from JSON array [min, max].
     */
    public static ClusteringConfig of(boolean enabled, int[] clusterSize) {
        if (!enabled || clusterSize == null || clusterSize.length < 2) {
            return DISABLED;
        }
        return new ClusteringConfig(true, clusterSize[0], clusterSize[1]);
    }
}
