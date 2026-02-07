package org.aouessar.core.gen.config;

/**
 * Distribution patterns for structure placement (trees, vegetation).
 */
public enum DistributionType {
    /** No placement */
    NONE,
    /** Even spread across the biome */
    UNIFORM,
    /** Random scattered placement */
    SCATTERED,
    /** Irregular patches */
    PATCHY,
    /** Grouped in clusters */
    CLUSTERED
}
