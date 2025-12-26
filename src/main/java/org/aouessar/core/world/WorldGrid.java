package org.aouessar.core.world;

import org.aouessar.core.math.CoordMath;
import org.aouessar.shared.EngineConfig;

/**
 * Canonical coordinate transforms:
 * - world blocks <-> chunks
 * - chunks <-> regions
 *
 * Handles negative coordinates correctly using floorDiv/floorMod.
 */
public final class WorldGrid {
    private WorldGrid() {}

    // -----------------------------
    // Chunk <-> World blocks
    // -----------------------------

    /** World X block coordinate of the chunk origin (min block) for chunk cx. */
    public static int chunkOriginBlockX(int cx) {
        return cx * EngineConfig.CHUNK_SIZE;
    }

    /** World Z block coordinate of the chunk origin (min block) for chunk cz. */
    public static int chunkOriginBlockZ(int cz) {
        return cz * EngineConfig.CHUNK_SIZE;
    }

    /** Chunk coordinate (cx) containing world block wx. */
    public static int worldBlockToChunkX(int wx) {
        return CoordMath.floorDiv(wx, EngineConfig.CHUNK_SIZE);
    }

    /** Chunk coordinate (cz) containing world block wz. */
    public static int worldBlockToChunkZ(int wz) {
        return CoordMath.floorDiv(wz, EngineConfig.CHUNK_SIZE);
    }

    /** Local block coordinate inside a chunk for world block wx: 0..CHUNK_SIZE-1. */
    public static int worldBlockToLocalInChunkX(int wx) {
        return CoordMath.floorMod(wx, EngineConfig.CHUNK_SIZE);
    }

    /** Local block coordinate inside a chunk for world block wz: 0..CHUNK_SIZE-1. */
    public static int worldBlockToLocalInChunkZ(int wz) {
        return CoordMath.floorMod(wz, EngineConfig.CHUNK_SIZE);
    }

    // -----------------------------
    // Region <-> Chunks
    // -----------------------------

    /** Region coordinate containing chunk cx. */
    public static int chunkToRegionX(int cx) {
        return CoordMath.floorDiv(cx, EngineConfig.REGION_SIZE_CHUNKS);
    }

    /** Region coordinate containing chunk cz. */
    public static int chunkToRegionZ(int cz) {
        return CoordMath.floorDiv(cz, EngineConfig.REGION_SIZE_CHUNKS);
    }

    /** Local chunk coordinate inside region for chunk cx: 0..REGION_SIZE_CHUNKS-1. */
    public static int chunkToLocalInRegionX(int cx) {
        return CoordMath.floorMod(cx, EngineConfig.REGION_SIZE_CHUNKS);
    }

    /** Local chunk coordinate inside region for chunk cz: 0..REGION_SIZE_CHUNKS-1. */
    public static int chunkToLocalInRegionZ(int cz) {
        return CoordMath.floorMod(cz, EngineConfig.REGION_SIZE_CHUNKS);
    }

    public static RegionPos regionOfChunk(int cx, int cz) {
        return new RegionPos(chunkToRegionX(cx), chunkToRegionZ(cz));
    }

    // -----------------------------
    // Region origin in world blocks
    // -----------------------------

    /** World X block coordinate of the region origin (min block) for region rx. */
    public static int regionOriginBlockX(int rx) {
        return rx * EngineConfig.REGION_SIZE_BLOCKS;
    }

    /** World Z block coordinate of the region origin (min block) for region rz. */
    public static int regionOriginBlockZ(int rz) {
        return rz * EngineConfig.REGION_SIZE_BLOCKS;
    }
}