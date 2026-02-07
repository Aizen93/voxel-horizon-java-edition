package org.aouessar.core.gen.config;

import org.aouessar.core.world.Blocks;

import java.util.List;

/**
 * Complete configuration for a single biome.
 */
public record BiomeConfig(
    String name,
    List<Short> surfaceBlocks,
    StructuresConfig structures
) {
    /**
     * Returns the primary surface (top) block for this biome.
     * Falls back to GRASS if not specified.
     */
    public short topBlock() {
        return surfaceBlocks.isEmpty() ? Blocks.GRASS : surfaceBlocks.get(0);
    }

    /**
     * Returns the filler block for this biome (used below surface).
     * Falls back to DIRT if not specified.
     */
    public short fillerBlock() {
        if (surfaceBlocks.size() >= 2) {
            return surfaceBlocks.get(1);
        }
        // Default filler based on top block
        short top = topBlock();
        if (top == Blocks.DESERT_SAND) return Blocks.DESERT_SANDSTONE;
        if (top == Blocks.SAND) return Blocks.SANDSTONE;
        return Blocks.DIRT;
    }

    /**
     * Returns the depth of the filler layer.
     */
    public int fillerDepth() {
        // Desert-like biomes have deeper fill
        short top = topBlock();
        if (top == Blocks.DESERT_SAND || top == Blocks.SAND) return 6;
        return 4;
    }
}
