package org.aouessar.renderer.mesh;

/**
 * CPU-side mesh buffers.
 *
 * Vertex layout (float):
 *   [x, y, z, u, v]  -> 5 floats per vertex
 *
 * Indices are 32-bit (int).
 */
public final class MeshData {
    public final float[] vertices;
    public final int[] indices;

    public MeshData(float[] vertices, int[] indices) {
        this.vertices = vertices;
        this.indices = indices;
        //System.out.println("vertexCount = " + vertexCount());
        //System.out.println("indexCount = " + indexCount());
    }

    public int vertexCount() {
        return vertices.length / 5;
    }

    public int indexCount() {
        return indices.length;
    }

    public boolean isEmpty() {
        return indices.length == 0;
    }
}