package org.aouessar.renderer.world;

import org.aouessar.core.world.Blocks;
import org.aouessar.renderer.mesh.Face;

import java.util.HashMap;
import java.util.Map;

public final class BlockRenderMap {

    private final Map<Short, FaceTiles> map = new HashMap<>();

    public BlockRenderMap() {
        // Defaults (adjust to match your atlas names)
        putAllFaces(Blocks.DIRT, "dirt");
        putAllFaces(Blocks.STONE, "stone");
        putAllFaces(Blocks.SAND, "sand");
        putAllFaces(Blocks.SNOW, "snow");
        putAllFaces(Blocks.WATER, "water");
        putAllFaces(Blocks.BUSH, "tree_leaves");

        // Grass: top/side/bottom
        map.put(Blocks.GRASS, new FaceTiles(
                "grass_side", // +X
                "grass_side", // -X
                "grass",      // +Y (top)
                "dirt",       // -Y (bottom)
                "grass_side", // +Z
                "grass_side"  // -Z
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