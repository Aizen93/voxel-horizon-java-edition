package org.aouessar.renderer.atlas;

import java.util.Map;

public final class Atlas {

    public record UvRect(float u0, float v0, float u1, float v1) {}

    private final Map<String, UvRect> tiles;

    public Atlas(Map<String, UvRect> tiles) {
        this.tiles = Map.copyOf(tiles);
    }

    public UvRect uv(String tileName) {
        UvRect uv = tiles.get(tileName);
        if (uv == null) {
            throw new IllegalArgumentException("Atlas tile not found: " + tileName);
        }
        return uv;
    }
}