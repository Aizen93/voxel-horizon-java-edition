package org.aouessar.renderer.world;

import org.aouessar.renderer.RendererConfig;
import org.aouessar.renderer.gl.GlShaderProgram;
import org.joml.Matrix4f;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * Wireframe box around the block the player is aiming at (GL_LINES,
 * slightly inflated to avoid z-fighting, depth-tested so it hugs terrain).
 * Reuses the ambient color-vertex shader.
 */
public final class BlockHighlight implements AutoCloseable {

    private final GlShaderProgram shader;
    private final int vao;
    private final int vbo;
    private final float[] verts = new float[24 * 7];

    public BlockHighlight() {
        this.shader = new GlShaderProgram(
                RendererConfig.AMBIENT_VERT,
                RendererConfig.AMBIENT_FRAG
        );
        this.vao = glGenVertexArrays();
        this.vbo = glGenBuffers();
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, (long) verts.length * Float.BYTES, GL_STREAM_DRAW);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 7 * Float.BYTES, 0L);
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(1, 4, GL_FLOAT, false, 7 * Float.BYTES, 3L * Float.BYTES);
        glBindVertexArray(0);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
    }

    public void draw(Matrix4f mvp, int bx, int by, int bz) {
        final float e = 0.004f; // inflate
        float x0 = bx - e, x1 = bx + 1 + e;
        float y0 = by - e, y1 = by + 1 + e;
        float z0 = bz - e, z1 = bz + 1 + e;

        // 12 edges = 24 line vertices
        float[][] p = {
                {x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}, {x0, y0, z1},
                {x0, y1, z0}, {x1, y1, z0}, {x1, y1, z1}, {x0, y1, z1},
        };
        int[] edges = {0, 1, 1, 2, 2, 3, 3, 0, 4, 5, 5, 6, 6, 7, 7, 4, 0, 4, 1, 5, 2, 6, 3, 7};

        int o = 0;
        for (int idx : edges) {
            verts[o++] = p[idx][0];
            verts[o++] = p[idx][1];
            verts[o++] = p[idx][2];
            verts[o++] = 0.05f;
            verts[o++] = 0.05f;
            verts[o++] = 0.05f;
            verts[o++] = 0.9f;
        }

        shader.use();
        shader.setUniformMat4("uMVP", mvp);
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferSubData(GL_ARRAY_BUFFER, 0L, verts);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glDrawArrays(GL_LINES, 0, 24);
        glDisable(GL_BLEND);
        glBindVertexArray(0);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
    }

    @Override
    public void close() {
        glDeleteBuffers(vbo);
        glDeleteVertexArrays(vao);
        shader.close();
    }
}
