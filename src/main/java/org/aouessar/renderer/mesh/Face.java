package org.aouessar.renderer.mesh;

/** Face indices shared between mesher + block render map. */
public final class Face {
    private Face() {}

    public static final int PX = 0; // +X
    public static final int NX = 1; // -X
    public static final int PY = 2; // +Y (top)
    public static final int NY = 3; // -Y (bottom)
    public static final int PZ = 4; // +Z
    public static final int NZ = 5; // -Z
}