package org.aouessar.renderer.ui;

import org.aouessar.renderer.BlockInteraction;
import org.aouessar.renderer.atlas.Atlas;
import org.aouessar.renderer.gl.GlShaderProgram;
import org.aouessar.renderer.mesh.Face;
import org.aouessar.renderer.world.BlockRenderMap;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * Immediate-mode screen-space UI: crosshair, the 9-slot hotbar with atlas
 * block icons, and the pause-menu dim. Rebuilt each frame, one draw call.
 */
public final class UiOverlay implements AutoCloseable {

    private static final int STRIDE = 8; // x y u v r g b a

    private final GlShaderProgram shader;
    private final int vao;
    private final int vbo;
    private final List<float[]> quads = new ArrayList<>();
    private float[] verts = new float[8 * 1024];

    private final Atlas atlas;
    private final BlockRenderMap brm;

    public UiOverlay(Atlas atlas, BlockRenderMap brm) {
        this.atlas = atlas;
        this.brm = brm;
        this.shader = new GlShaderProgram("/shaders/ui_quad.vert", "/shaders/ui_quad.frag");
        this.vao = glGenVertexArrays();
        this.vbo = glGenBuffers();
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, (long) verts.length * Float.BYTES, GL_STREAM_DRAW);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 2, GL_FLOAT, false, STRIDE * Float.BYTES, 0L);
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, STRIDE * Float.BYTES, 2L * Float.BYTES);
        glEnableVertexAttribArray(2);
        glVertexAttribPointer(2, 4, GL_FLOAT, false, STRIDE * Float.BYTES, 4L * Float.BYTES);
        glBindVertexArray(0);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
    }

    /** Queue a flat-color rectangle (public: the pause menu draws through us). */
    public void queueRect(float x, float y, float w, float h, float r, float g, float b, float a) {
        rect(x, y, w, h, r, g, b, a);
    }

    /** Draw crosshair + hotbar (+ optional menu dim). Call after the HUD. */
    public void render(int w, int h, BlockInteraction interaction, boolean menuDim, int atlasUnit) {
        quads.clear();

        if (menuDim) {
            rect(0, 0, w, h, 0f, 0f, 0f, 0.55f);
        } else {
            // Crosshair
            float cx = w / 2f, cy = h / 2f;
            rect(cx - 8, cy - 1, 16, 2, 1f, 1f, 1f, 0.75f);
            rect(cx - 1, cy - 8, 2, 16, 1f, 1f, 1f, 0.75f);
        }

        // Hotbar: 9 slots, bottom center
        float slot = 44, pad = 4, icon = slot - 10;
        float total = 9 * slot + 8 * pad;
        float x0 = (w - total) / 2f, y0 = h - slot - 10;
        for (int i = 0; i < 9; i++) {
            float x = x0 + i * (slot + pad);
            boolean sel = (i == interaction.selectedSlot());
            rect(x, y0, slot, slot, 0f, 0f, 0f, sel ? 0.75f : 0.45f);
            if (sel) {
                rect(x - 2, y0 - 2, slot + 4, 2, 1f, 1f, 1f, 0.95f);
                rect(x - 2, y0 + slot, slot + 4, 2, 1f, 1f, 1f, 0.95f);
                rect(x - 2, y0, 2, slot, 1f, 1f, 1f, 0.95f);
                rect(x + slot, y0, 2, slot, 1f, 1f, 1f, 0.95f);
            }
            Atlas.UvRect uv = atlas.uv(brm.tileName(BlockInteraction.PALETTE[i], Face.PY));
            texRect(x + 5, y0 + 5, icon, icon, uv.u0(), uv.v0(), uv.u1(), uv.v1());
        }

        flushQueued(w, h, atlasUnit);
    }

    /** Upload + draw everything queued since the last flush, then clear. */
    public void flushQueued(int w, int h, int texUnit) {
        if (quads.isEmpty()) return;
        int n = quads.size() * 6 * STRIDE;
        if (verts.length < n) verts = new float[n];
        int o = 0;
        for (float[] q : quads) {
            System.arraycopy(q, 0, verts, o, q.length);
            o += q.length;
        }
        quads.clear();

        shader.use();
        shader.setUniform2f("uScreen", w, h);
        shader.setUniform1i("uTex", texUnit);

        glDisable(GL_DEPTH_TEST);
        glDepthMask(false);
        glDisable(GL_CULL_FACE); // screen-space y-flip reverses winding
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, (long) verts.length * Float.BYTES, GL_STREAM_DRAW);
        glBufferSubData(GL_ARRAY_BUFFER, 0L, java.util.Arrays.copyOf(verts, o));
        glDrawArrays(GL_TRIANGLES, 0, o / STRIDE);
        glBindVertexArray(0);
        glDisable(GL_BLEND);
        glEnable(GL_CULL_FACE);
        glDepthMask(true);
        glEnable(GL_DEPTH_TEST);
    }

    private void rect(float x, float y, float w, float h, float r, float g, float b, float a) {
        quad(x, y, w, h, -1f, -1f, -1f, -1f, r, g, b, a);
    }

    private void texRect(float x, float y, float w, float h, float u0, float v0, float u1, float v1) {
        quad(x, y, w, h, u0, v0, u1, v1, 1f, 1f, 1f, 1f);
    }

    private void quad(float x, float y, float w, float h,
                      float u0, float v0, float u1, float v1,
                      float r, float g, float b, float a) {
        float[] q = new float[6 * STRIDE];
        float[][] vs = {
                {x, y, u0, v0}, {x + w, y, u1, v0}, {x + w, y + h, u1, v1},
                {x + w, y + h, u1, v1}, {x, y + h, u0, v1}, {x, y, u0, v0},
        };
        int o = 0;
        for (float[] v : vs) {
            q[o++] = v[0];
            q[o++] = v[1];
            q[o++] = v[2];
            q[o++] = v[3];
            q[o++] = r;
            q[o++] = g;
            q[o++] = b;
            q[o++] = a;
        }
        quads.add(q);
    }

    @Override
    public void close() {
        glDeleteBuffers(vbo);
        glDeleteVertexArrays(vao);
        shader.close();
    }
}
