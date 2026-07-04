package org.aouessar.renderer.gl;

import org.aouessar.renderer.mesh.MeshData;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * GPU mesh for far-field LOD tiles.
 * Vertex layout (9 floats):
 *   location 0 : vec3 position (render space)
 *   location 1 : vec3 color
 *   location 2 : vec3 normal
 */
public final class GlMeshLod implements IGlMesh {

    public static final int STRIDE_FLOATS = 9;

    private final int vao;
    private final int vbo;
    private final int ebo;
    private final int indexCount;

    public GlMeshLod(MeshData mesh) {
        if (mesh.floatsPerVertex != STRIDE_FLOATS) {
            throw new IllegalArgumentException(
                    "GlMeshLod expects " + STRIDE_FLOATS + " floats/vertex, got " + mesh.floatsPerVertex);
        }
        int strideBytes = STRIDE_FLOATS * Float.BYTES;

        this.indexCount = mesh.indices.length;

        vao = glGenVertexArrays();
        vbo = glGenBuffers();
        ebo = glGenBuffers();

        glBindVertexArray(vao);

        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        FloatBuffer vb = MemoryUtil.memAllocFloat(mesh.vertices.length);
        vb.put(mesh.vertices).flip();
        glBufferData(GL_ARRAY_BUFFER, vb, GL_STATIC_DRAW);
        MemoryUtil.memFree(vb);

        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo);
        IntBuffer ib = MemoryUtil.memAllocInt(mesh.indices.length);
        ib.put(mesh.indices).flip();
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, ib, GL_STATIC_DRAW);
        MemoryUtil.memFree(ib);

        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, strideBytes, 0L);

        glEnableVertexAttribArray(1);
        glVertexAttribPointer(1, 3, GL_FLOAT, false, strideBytes, 3L * Float.BYTES);

        glEnableVertexAttribArray(2);
        glVertexAttribPointer(2, 3, GL_FLOAT, false, strideBytes, 6L * Float.BYTES);

        glBindVertexArray(0);
    }

    @Override
    public void draw() {
        glBindVertexArray(vao);
        glDrawElements(GL_TRIANGLES, indexCount, GL_UNSIGNED_INT, 0L);
    }

    @Override
    public void close() {
        glBindVertexArray(0);
        glDeleteBuffers(vbo);
        glDeleteBuffers(ebo);
        glDeleteVertexArrays(vao);
    }
}
