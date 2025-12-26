package org.aouessar.core.math;

import org.aouessar.shared.EngineConfig;

public final class Height {
    private Height() {}

    /** Converts world Y (e.g., -64..319) to local array Y (0..WORLD_HEIGHT-1). */
    public static int toLocalY(int worldY) {
        return worldY - EngineConfig.MIN_Y;
    }

    /** Converts local array Y (0..WORLD_HEIGHT-1) back to world Y (-64..319). */
    public static int toWorldY(int localY) {
        return localY + EngineConfig.MIN_Y;
    }

    public static boolean isValidWorldY(int worldY) {
        return worldY >= EngineConfig.MIN_Y && worldY <= EngineConfig.MAX_Y;
    }

    public static boolean isValidLocalY(int localY) {
        return localY >= 0 && localY < EngineConfig.WORLD_HEIGHT;
    }

    /** Clamps worldY to the valid world range. */
    public static int clampWorldY(int worldY) {
        return Math.max(EngineConfig.MIN_Y, Math.min(EngineConfig.MAX_Y, worldY));
    }
}