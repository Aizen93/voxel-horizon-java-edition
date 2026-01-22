package org.aouessar.renderer.world;

import org.aouessar.core.world.Blocks;
import org.aouessar.renderer.mesh.Face;

import java.util.HashMap;
import java.util.Map;

public final class BlockRenderMap {

    private final Map<Short, FaceTiles> map = new HashMap<>();

    public BlockRenderMap() {
        // Defaults (adjust to match your atlas names)
        putAllFaces(Blocks.BEDROCK, "bedrock");
        putAllFaces(Blocks.BUSH, "bush");
        putAllFaces(Blocks.BERRY_BUSH, "sweet_berry_bush");

        map.put(Blocks.CACTUS, new FaceTiles(
            "cactus_side", // +X
            "cactus_side", // -X
            "cactus_top",      // +Y (top)
            "cactus_bottom",       // -Y (bottom)
            "cactus_side", // +Z
            "cactus_side"  // -Z
        ));

        putAllFaces(Blocks.CLAY, "clay");
        putAllFaces(Blocks.DEEPSLATE, "deepslate");
        putAllFaces(Blocks.DIRT, "dirt2");
        putAllFaces(Blocks.FLOWER_RED, "flower_red");
        putAllFaces(Blocks.FLOWER_YELLOW, "flower_yellow");
        putAllFaces(Blocks.GLASS, "glass");

        // Grass: top/side/bottom
        map.put(Blocks.GRASS, new FaceTiles(
            "grass_side2", // +X
            "grass_side2", // -X
            "grass2",      // +Y (top)
            "dirt2",       // -Y (bottom)
            "grass_side2", // +Z
            "grass_side2"  // -Z
        ));

        putAllFaces(Blocks.GRAVEL, "gravel");
        putAllFaces(Blocks.ICE, "ice");

        putAllFaces(Blocks.OAK_LEAVES, "oak_leaves");
        map.put(Blocks.OAK_LOG, new FaceTiles(
            "oak_log", // +X
            "oak_log", // -X
            "oak_log_bot", // +Y (top)
            "oak_log_bot", // -Y (bottom)
            "oak_log", // +Z
            "oak_log"  // -Z
        ));

        putAllFaces(Blocks.SAND, "sand");

        putAllFaces(Blocks.SANDSTONE, "sandstone");
        putAllFaces(Blocks.SNOW, "snow");
        putAllFaces(Blocks.STONE, "stone");

        putAllFaces(Blocks.TALL_GRASS, "tall_grass");
        putAllFaces(Blocks.WATER, "water");
        putAllFaces(Blocks.DESERT_SAND, "red_sand");
        putAllFaces(Blocks.DESERT_SANDSTONE, "red_sandstone");

        map.put(Blocks.PODZOl_DIRT, new FaceTiles(
            "dirt_podzol_side", // +X
            "dirt_podzol_side", // -X
            "dirt_podzol_top", // +Y (top)
            "dirt", // -Y (bottom)
            "dirt_podzol_side", // +Z
            "dirt_podzol_side"  // -Z
        ));

        map.put(Blocks.SNOW_GRASS, new FaceTiles(
            "grass_side_snowed", // +X
            "grass_side_snowed", // -X
            "snow", // +Y (top)
            "snow", // -Y (bottom)
            "grass_side_snowed", // +Z
            "grass_side_snowed"  // -Z
        ));

        map.put(Blocks.DRY_GRASS, new FaceTiles(
            "dry_grass_side", // +X
            "dry_grass_side", // -X
            "dry_grass_top", // +Y (top)
            "dirt", // -Y (bottom)
            "dry_grass_side", // +Z
            "dry_grass_side"  // -Z
        ));

        putAllFaces(Blocks.DRY_WHEAT, "dry_wheat");

        putAllFaces(Blocks.ACACIA_LEAVES, "acacia_leaves");
        map.put(Blocks.ACACIA_LOG, new FaceTiles(
                "acacia_log_side", // +X
                "acacia_log_side", // -X
                "acacia_log_top", // +Y (top)
                "acacia_log_top", // -Y (bottom)
                "acacia_log_side", // +Z
                "acacia_log_side"  // -Z
        ));

        putAllFaces(Blocks.SNOW_LEAVES, "snow_leaves");
        map.put(Blocks.SNOW_LOG, new FaceTiles(
                "oak_log", // +X
                "oak_log", // -X
                "oak_log_bot", // +Y (top)
                "oak_log_bot", // -Y (bottom)
                "oak_log", // +Z
                "oak_log"  // -Z
        ));

        putAllFaces(Blocks.SPRUCE_LEAVES, "spruce_leaves");
        map.put(Blocks.SPRUCE_LOG, new FaceTiles(
                "spruce_log", // +X
                "spruce_log", // -X
                "spruce_log_top", // +Y (top)
                "spruce_log_top", // -Y (bottom)
                "spruce_log", // +Z
                "spruce_log"  // -Z
        ));

        putAllFaces(Blocks.JUNGLE_LEAVES, "jungle_leaves");
        map.put(Blocks.JUNGLE_LOG, new FaceTiles(
                "jungle_log_side", // +X
                "jungle_log_side", // -X
                "jungle_log_top", // +Y (top)
                "jungle_log_top", // -Y (bottom)
                "jungle_log_side", // +Z
                "jungle_log_side"  // -Z
        ));
    }

    private void putAllFaces(short blockId, String tile) {
        map.put(blockId, new FaceTiles(tile, tile, tile, tile, tile, tile));
    }

    /** Returns atlas tile name for that block + face. */
    public String tileName(short blockId, int face) {
        FaceTiles t = map.get(blockId);
        if (t == null) return "stone"; // safe fallback for v1
        return switch (face) {
            case Face.PX -> t.px;
            case Face.NX -> t.nx;
            case Face.PY -> t.py;
            case Face.NY -> t.ny;
            case Face.PZ -> t.pz;
            case Face.NZ -> t.nz;
            default -> throw new IllegalArgumentException("Unknown face: " + face);
        };
    }

    private record FaceTiles(String px, String nx, String py, String ny, String pz, String nz) {}
}