package org.aouessar.core.gen.config;

import java.util.List;

/**
 * Configuration for structure placement in a biome.
 * Contains both tree and vegetation configurations.
 */
public record StructuresConfig(
    TreesConfig trees,
    VegetationConfig vegetation
) {
    public static final StructuresConfig EMPTY = new StructuresConfig(
        TreesConfig.EMPTY, VegetationConfig.EMPTY
    );
}
