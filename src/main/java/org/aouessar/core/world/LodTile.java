package org.aouessar.core.world;

import org.aouessar.shared.EngineConfig;

/**
 * Immutable far-field LOD sample tile.
 * <p>
 * A tile covers exactly one region footprint (REGION_SIZE_BLOCKS square) and
 * holds per-column samples on a regular grid of spacing {@code step} blocks:
 * surface height, water level and top (visible) block.
 * <p>
 * The grid includes ONE extra sample ring around the tile
 * (i, j in -1 .. cells+1) so consumers can compute smooth normals and stitch
 * neighboring tiles without extra lookups. Sample (i, j) is at world
 * ({@code originX + i*step}, {@code originZ + j*step}).
 * <p>
 * Heights and water levels are world Y. This type is engine-agnostic and safe
 * to hand to any renderer.
 */
public final class LodTile {

    /** Sentinel: no water in this column. */
    public static final int NO_WATER = Integer.MIN_VALUE;

    private final int tileX;
    private final int tileZ;
    private final int step;
    private final int cells;     // samples 0..cells span the full tile edge
    private final int grid;      // samples per side including the border ring = cells + 3

    private final int[] height;      // world Y
    private final int[] waterLevel;  // world Y or NO_WATER
    private final short[] topBlock;

    public LodTile(int tileX, int tileZ, int step, int[] height, int[] waterLevel, short[] topBlock) {
        if (step <= 0 || EngineConfig.REGION_SIZE_BLOCKS % step != 0) {
            throw new IllegalArgumentException("step must divide REGION_SIZE_BLOCKS: " + step);
        }
        this.tileX = tileX;
        this.tileZ = tileZ;
        this.step = step;
        this.cells = EngineConfig.REGION_SIZE_BLOCKS / step;
        this.grid = cells + 3;

        int expected = grid * grid;
        if (height.length != expected || waterLevel.length != expected || topBlock.length != expected) {
            throw new IllegalArgumentException("LodTile array length mismatch (expected " + expected + ")");
        }
        this.height = height;
        this.waterLevel = waterLevel;
        this.topBlock = topBlock;
    }

    public int tileX() { return tileX; }
    public int tileZ() { return tileZ; }
    public int step() { return step; }

    /** Number of cells per side; vertices run 0..cells inclusive. */
    public int cells() { return cells; }

    /** Samples per side including the -1 / cells+1 border ring. */
    public int gridSize() { return grid; }

    public int originX() { return tileX * EngineConfig.REGION_SIZE_BLOCKS; }
    public int originZ() { return tileZ * EngineConfig.REGION_SIZE_BLOCKS; }

    /** i, j in -1 .. cells+1. */
    public int index(int i, int j) {
        return (j + 1) * grid + (i + 1);
    }

    public int heightAt(int i, int j) { return height[index(i, j)]; }
    public int waterLevelAt(int i, int j) { return waterLevel[index(i, j)]; }
    public short topBlockAt(int i, int j) { return topBlock[index(i, j)]; }
}
