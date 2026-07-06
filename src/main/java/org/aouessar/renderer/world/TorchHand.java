package org.aouessar.renderer.world;

import org.aouessar.renderer.RendererConfig;
import org.aouessar.renderer.gl.GlShaderProgram;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * Held-torch viewmodel: a little wooden stick with a glowing flame, fixed to
 * the bottom-right of the view (Minecraft held-item style). Vertices are baked
 * in VIEW space, so drawing needs only the (jittered) projection matrix — it
 * renders into the HDR scene before post, so the flame feeds bloom and picks
 * up the same tonemap as the world.
 */
public final class TorchHand implements AutoCloseable {

    private final GlShaderProgram shader;
    private final int vao;
    private final int vbo;
    private final int vertexCount;

    public TorchHand() {
        this.shader = new GlShaderProgram(
                RendererConfig.TORCH_HAND_VERT,
                RendererConfig.TORCH_HAND_FRAG
        );

        float[] verts = buildMesh();
        this.vertexCount = verts.length / 6;

        this.vao = glGenVertexArrays();
        this.vbo = glGenBuffers();
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, verts, GL_STATIC_DRAW);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 6 * Float.BYTES, 0L);
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(1, 3, GL_FLOAT, false, 6 * Float.BYTES, 3L * Float.BYTES);
        glBindVertexArray(0);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
    }

    /**
     * Draw over the finished world (depth test off, into the HDR target).
     *
     * @param proj    projection matrix (jittered, same as the world this frame)
     * @param time    seconds, for the idle sway
     * @param flicker flame brightness: flame flicker x light fade (0 = out)
     */
    public void draw(Matrix4f proj, float time, float flicker) {
        shader.use();
        shader.setUniformMat4("uProj", proj);
        shader.setUniform1f("uTime", time);
        shader.setUniform1f("uFlicker", flicker);

        boolean cull = glIsEnabled(GL_CULL_FACE);
        boolean depth = glIsEnabled(GL_DEPTH_TEST);
        if (cull) glDisable(GL_CULL_FACE);
        if (depth) glDisable(GL_DEPTH_TEST);
        glDepthMask(false);

        glBindVertexArray(vao);
        glDrawArrays(GL_TRIANGLES, 0, vertexCount);
        glBindVertexArray(0);

        glDepthMask(true);
        if (depth) glEnable(GL_DEPTH_TEST);
        if (cull) glEnable(GL_CULL_FACE);
    }

    @Override
    public void close() {
        glDeleteBuffers(vbo);
        glDeleteVertexArrays(vao);
        shader.close();
    }

    // -------------------------------------------------------------------------
    // Mesh: three tilted boxes (stick, charred cap, HDR flame) in view space
    // -------------------------------------------------------------------------

    private static float[] buildMesh() {
        List<float[]> tris = new ArrayList<>();

        // Local frame around the grip, tilted like a held torch, then moved
        // to the bottom-right of the view (view space: x right, y up, -z forward).
        Matrix4f xform = new Matrix4f()
                .translate(0.52f, -0.42f, -0.70f)
                .rotateZ(-0.20f)
                .rotateX(0.12f)
                .scale(0.60f);

        // Wooden stick
        addBox(tris, xform, 0f, 0.09f, 0f, 0.016f, 0.115f, 0.016f,
                0.46f, 0.31f, 0.16f, false);
        // Charred wrap under the flame
        addBox(tris, xform, 0f, 0.215f, 0f, 0.021f, 0.017f, 0.021f,
                0.16f, 0.13f, 0.11f, false);
        // Flame: HDR color (r > 1 tags it for the flicker in the shader)
        addBox(tris, xform, 0f, 0.252f, 0f, 0.014f, 0.022f, 0.014f,
                2.6f, 1.55f, 0.55f, true);

        float[] out = new float[tris.size() * 3 * 6];
        int o = 0;
        for (float[] tri : tris) {
            System.arraycopy(tri, 0, out, o, tri.length);
            o += tri.length;
        }
        return out;
    }

    /** Axis-aligned box in the local frame, baked face shading, transformed. */
    private static void addBox(
            List<float[]> tris, Matrix4f xform,
            float cx, float cy, float cz,
            float hx, float hy, float hz,
            float r, float g, float b,
            boolean emissive
    ) {
        // face order: +X -X +Y -Y +Z -Z ; simple baked light per face
        float[] faceShade = emissive
                ? new float[]{1f, 1f, 1f, 1f, 1f, 1f}
                : new float[]{0.80f, 0.80f, 1.00f, 0.50f, 0.70f, 0.70f};

        float x0 = cx - hx, x1 = cx + hx;
        float y0 = cy - hy, y1 = cy + hy;
        float z0 = cz - hz, z1 = cz + hz;

        // 6 faces x 2 triangles; CCW not required (cull disabled while drawing)
        float[][][] faces = {
                {{x1, y0, z0}, {x1, y1, z0}, {x1, y1, z1}, {x1, y0, z1}}, // +X
                {{x0, y0, z1}, {x0, y1, z1}, {x0, y1, z0}, {x0, y0, z0}}, // -X
                {{x0, y1, z0}, {x0, y1, z1}, {x1, y1, z1}, {x1, y1, z0}}, // +Y
                {{x0, y0, z1}, {x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}}, // -Y
                {{x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}, {x0, y1, z1}}, // +Z
                {{x1, y0, z0}, {x0, y0, z0}, {x0, y1, z0}, {x1, y1, z0}}, // -Z
        };

        Vector3f v = new Vector3f();
        for (int f = 0; f < 6; f++) {
            float s = faceShade[f];
            float fr = r * s, fg = g * s, fb = b * s;
            float[][] q = faces[f];
            int[][] order = {{0, 1, 2}, {2, 3, 0}};
            for (int[] tri : order) {
                float[] data = new float[18];
                int o = 0;
                for (int idx : tri) {
                    v.set(q[idx][0], q[idx][1], q[idx][2]);
                    xform.transformPosition(v);
                    data[o++] = v.x;
                    data[o++] = v.y;
                    data[o++] = v.z;
                    data[o++] = fr;
                    data[o++] = fg;
                    data[o++] = fb;
                }
                tris.add(data);
            }
        }
    }
}
