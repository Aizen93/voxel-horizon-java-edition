package org.aouessar.core.world.layers;

import org.aouessar.core.math.Height;
import org.aouessar.shared.EngineConfig;

public final class Heightmap extends ArrayLayer2D {
    private final int[] height; // world Y per (wx,wz)

    public Heightmap(LayerRect rect, int[] height) {
        super(rect);
        if (height.length != rect.sizeX * rect.sizeZ) {
            throw new IllegalArgumentException("Heightmap array length mismatch");
        }
        this.height = height;
    }

    /** Returns world Y (MIN_Y..MAX_Y). Safe: clamps if sampling outside. */
    public int heightAt(int wx, int wz) {
        if (!contains(wx, wz)) {
            // Gameplay safety: never throw.
            return EngineConfig.SEA_LEVEL;
        }
        int h = height[index(wx, wz)];
        return Height.clampWorldY(h);
    }

    /** Fast path when caller guarantees rect containment. */
    public int heightAtUnchecked(int wx, int wz) {
        return Height.clampWorldY(height[index(wx, wz)]);
    }

    public int[] rawHeights() {
        return height;
    }
}