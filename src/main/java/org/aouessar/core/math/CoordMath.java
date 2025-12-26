package org.aouessar.core.math;

public final class CoordMath {
    private CoordMath() {}

    /** Like Math.floorDiv but explicit for readability in world math. */
    public static int floorDiv(int a, int b) {
        return Math.floorDiv(a, b);
    }

    /** Like Math.floorMod but explicit for readability in world math. */
    public static int floorMod(int a, int b) {
        return Math.floorMod(a, b);
    }
}