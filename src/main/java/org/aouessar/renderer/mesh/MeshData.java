package org.aouessar.renderer.mesh;

/**
 * CPU-side mesh buffers.
 * The vertex layout is owned by whoever builds/uploads the mesh;
 * {@code floatsPerVertex} records the stride so counts stay correct
 * (chunk meshes use 7 floats, LOD meshes use 9).
 * Indices are 32-bit (int).
 */
public final class MeshData {
    public final float[] vertices;
    public final int[] indices;
    public final int floatsPerVertex;

    public MeshData(float[] vertices, int[] indices, int floatsPerVertex) {
        if (floatsPerVertex <= 0 || vertices.length % floatsPerVertex != 0) {
            throw new IllegalArgumentException(
                    "vertices.length=" + vertices.length + " is not a multiple of stride " + floatsPerVertex);
        }
        this.vertices = vertices;
        this.indices = indices;
        this.floatsPerVertex = floatsPerVertex;
    }

    public int vertexCount() {
        return vertices.length / floatsPerVertex;
    }

    public int indexCount() {
        return indices.length;
    }

    public boolean isEmpty() {
        return indices.length == 0;
    }
}
