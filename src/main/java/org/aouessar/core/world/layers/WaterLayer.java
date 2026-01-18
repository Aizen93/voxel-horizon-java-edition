package org.aouessar.core.world.layers;

/**
 * A layer that stores the water level for each column.
 * <p>
 * For each (wx, wz):
 * <ul>
 *   <li>{@code waterLevel[i] > MIN_Y} means water fills from that level down to the surface</li>
 *   <li>{@code waterLevel[i] == NO_WATER} means no water in that column</li>
 * </ul>
 * <p>
 * This separates water placement from the chunk composition logic,
 * making rivers, lakes, and ocean handling cleaner and more flexible.
 */
public final class WaterLayer extends ArrayLayer2D {

    /** Sentinel value indicating no water in this column. */
    public static final int NO_WATER = Integer.MIN_VALUE;

    private final int[] waterLevel;

    public WaterLayer(LayerRect rect, int[] waterLevel) {
        super(rect);
        if (waterLevel.length != rect.sizeX * rect.sizeZ) {
            throw new IllegalArgumentException("WaterLayer array length mismatch");
        }
        this.waterLevel = waterLevel;
    }

    /**
     * Returns the water surface level at (wx, wz), or {@link #NO_WATER} if dry.
     * Safe: returns NO_WATER if outside rect.
     */
    public int waterLevelAt(int wx, int wz) {
        if (!contains(wx, wz)) return NO_WATER;
        return waterLevel[index(wx, wz)];
    }

    /**
     * Fast path when caller guarantees rect containment.
     */
    public int waterLevelAtUnchecked(int wx, int wz) {
        return waterLevel[index(wx, wz)];
    }

    /**
     * Returns true if this column has water (ocean, river, lake, etc.)
     */
    public boolean hasWater(int wx, int wz) {
        return waterLevelAt(wx, wz) != NO_WATER;
    }

    /**
     * Returns true if this column has water (fast path, no bounds check).
     */
    public boolean hasWaterUnchecked(int wx, int wz) {
        return waterLevel[index(wx, wz)] != NO_WATER;
    }

    public int[] raw() {
        return waterLevel;
    }
}
