package org.aouessar.renderer.gl;

import org.aouessar.renderer.mesh.MeshData;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

public final class GlMeshTiled implements IGlMesh {

    private final int vao;
    private final int vbo;
    private final int ebo;
    private final int indexCount;

    public GlMeshTiled(MeshData mesh) {
        final int STRIDE_FLOATS = 7;
        int strideBytes = STRIDE_FLOATS * Float.BYTES;

        if ((mesh.vertices.length % 7) != 0) {
            throw new IllegalArgumentException(
                    "GlMeshTiled expects 7 floats/vertex, got vertices.length=" + mesh.vertices.length
            );
        }

        this.indexCount = mesh.indices.length;

        vao = glGenVertexArrays();
        vbo = glGenBuffers();
        ebo = glGenBuffers();

        glBindVertexArray(vao);

        // ---------- VBO ----------
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        FloatBuffer vb = MemoryUtil.memAllocFloat(mesh.vertices.length);
        vb.put(mesh.vertices).flip();
        glBufferData(GL_ARRAY_BUFFER, vb, GL_STATIC_DRAW);
        MemoryUtil.memFree(vb);

        // ---------- EBO ----------
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo);
        IntBuffer ib = MemoryUtil.memAllocInt(mesh.indices.length);
        ib.put(mesh.indices).flip();
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, ib, GL_STATIC_DRAW);
        MemoryUtil.memFree(ib);

        // Layout:
        // location 0 : vec3 position
        // location 1 : vec2 tileMin (atlas UV origin)
        // location 2 : vec2 uvLocal (repeat space)
        // => total = 7 floats per vertex stride

        // aPos (location=0) vec3
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, strideBytes, 0L);

        // aTileMin (location=1) vec2
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, strideBytes, 3L * Float.BYTES);

        // aUvLocal (location=2) vec2
        glEnableVertexAttribArray(2);
        glVertexAttribPointer(2, 2, GL_FLOAT, false, strideBytes, 5L * Float.BYTES);

        glBindVertexArray(0);
    }

    @Override
    public void draw() {
        glBindVertexArray(vao);
        glDrawElements(GL_TRIANGLES, indexCount, GL_UNSIGNED_INT, 0L);
        glBindVertexArray(0);
    }

    @Override
    public void close() {
        glBindVertexArray(0);
        glDeleteBuffers(vbo);
        glDeleteBuffers(ebo);
        glDeleteVertexArrays(vao);
    }
}