package org.aouessar.renderer.ui;

import org.lwjgl.stb.STBEasyFont;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.opengl.GL33C.*;

public final class DebugOverlay implements AutoCloseable {

    // stb_easy_font vertex format (per vertex):
    // x:float, y:float, z:float, color:ubyte[4]  => 16 bytes/vertex
    private static final int VERTEX_STRIDE_BYTES = 16;
    private static final int VERTS_PER_QUAD = 4;
    private static final int INDICES_PER_QUAD = 6;

    private final int maxQuads;
    private final int vao;
    private final int vbo;
    private final int ebo;

    private final int bgVao;
    private final int bgVbo;

    private final int program;
    private final int uScreenLoc;

    private final int bgProgram;
    private final int bgUScreenLoc;
    private final int bgUColorLoc;

    private final ByteBuffer vertexBuffer; // direct, reused
    private final ByteBuffer whiteColor = MemoryUtil.memAlloc(4);

    private boolean enabled = true;
    private boolean showBackground = true;

    public DebugOverlay(int maxQuads) {
        this.maxQuads = Math.max(256, maxQuads);

        // RGBA white
        whiteColor.put(0, (byte) 255);
        whiteColor.put(1, (byte) 255);
        whiteColor.put(2, (byte) 255);
        whiteColor.put(3, (byte) 255);

        // Text buffers
        vertexBuffer = MemoryUtil.memAlloc(this.maxQuads * VERTS_PER_QUAD * VERTEX_STRIDE_BYTES);

        // --- TEXT VAO/VBO/EBO ---
        vao = glGenVertexArrays();
        vbo = glGenBuffers();
        ebo = glGenBuffers();

        glBindVertexArray(vao);

        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, (long) vertexBuffer.capacity(), GL_DYNAMIC_DRAW);

        // Prebuild indices for quads -> triangles
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo);
        IntBuffer indices = MemoryUtil.memAllocInt(this.maxQuads * INDICES_PER_QUAD);
        for (int q = 0; q < this.maxQuads; q++) {
            int baseV = q * 4;
            int baseI = q * 6;
            // (0,1,2) (2,3,0)
            indices.put(baseI,     baseV);
            indices.put(baseI + 1, baseV + 1);
            indices.put(baseI + 2, baseV + 2);
            indices.put(baseI + 3, baseV + 2);
            indices.put(baseI + 4, baseV + 3);
            indices.put(baseI + 5, baseV);
        }
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, indices, GL_STATIC_DRAW);
        MemoryUtil.memFree(indices);

        // layout(location=0) vec3 pos (float)
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, VERTEX_STRIDE_BYTES, 0L);

        // layout(location=1) vec4 color (ubyte normalized)
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(1, 4, GL_UNSIGNED_BYTE, true, VERTEX_STRIDE_BYTES, 12L);

        glBindVertexArray(0);

        // --- BACKGROUND quad (2 triangles), separate VAO/VBO ---
        bgVao = glGenVertexArrays();
        bgVbo = glGenBuffers();

        glBindVertexArray(bgVao);
        glBindBuffer(GL_ARRAY_BUFFER, bgVbo);
        glBufferData(GL_ARRAY_BUFFER, 6L * 2L * Float.BYTES, GL_DYNAMIC_DRAW); // 6 verts, vec2

        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 2 * Float.BYTES, 0L);

        glBindVertexArray(0);

        // --- Shaders ---
        program = compileProgram(TEXT_VS, TEXT_FS);
        uScreenLoc = glGetUniformLocation(program, "uScreen");

        bgProgram = compileProgram(BG_VS, BG_FS);
        bgUScreenLoc = glGetUniformLocation(bgProgram, "uScreen");
        bgUColorLoc = glGetUniformLocation(bgProgram, "uColor");
    }

    public DebugOverlay() {
        this(8192); // plenty for lots of lines
    }

    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isEnabled() { return enabled; }

    public void setShowBackground(boolean showBackground) { this.showBackground = showBackground; }
    public boolean isShowBackground() { return showBackground; }

    /**
     * Render a debug overlay at pixel position (x,y) where y increases downward.
     * Call this at the END of your frame (after 3D world draw).
     */
    public void render(int screenW, int screenH, float x, float y, CharSequence text) {
        if (!enabled) return;
        if (text == null || text.length() == 0) return;

        // Compute text bounds for background (Minecraft style)
        int textW = STBEasyFont.stb_easy_font_width(text);
        int textH = STBEasyFont.stb_easy_font_height(text);

        // --- Setup state for 2D overlay ---
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_CULL_FACE);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        try {
            if (showBackground) {
                drawBackground(screenW, screenH, x, y, textW, textH);
            }

            // Fill vertex buffer with stb_easy_font
            vertexBuffer.clear();
            int quads = STBEasyFont.stb_easy_font_print(x, y, text, whiteColor, vertexBuffer);
            if (quads <= 0) return;

            // Clamp to our max
            if (quads > maxQuads) quads = maxQuads;

            // stb wrote bytes into the ByteBuffer; set limit for upload
            int bytesToUpload = quads * VERTS_PER_QUAD * VERTEX_STRIDE_BYTES;
            vertexBuffer.limit(bytesToUpload);

            glUseProgram(program);
            glUniform2f(uScreenLoc, screenW, screenH);

            glBindVertexArray(vao);
            glBindBuffer(GL_ARRAY_BUFFER, vbo);
            glBufferSubData(GL_ARRAY_BUFFER, 0, vertexBuffer);

            glDrawElements(GL_TRIANGLES, quads * INDICES_PER_QUAD, GL_UNSIGNED_INT, 0L);

            glBindVertexArray(0);
            glUseProgram(0);
        } finally {
            // Restore OpenGL state for next frame's 3D rendering
            glEnable(GL_DEPTH_TEST);
            glEnable(GL_CULL_FACE);
            glDisable(GL_BLEND);
        }
    }

    private void drawBackground(int screenW, int screenH, float x, float y, int textW, int textH) {
        // small padding like MC
        float pad = 4f;
        float bx0 = x - pad;
        float by0 = y - pad;
        float bx1 = x + textW + pad;
        float by1 = y + textH + pad;

        // 2 triangles (x,y) in pixel coords
        float[] verts = new float[] {
                bx0, by0,
                bx1, by0,
                bx1, by1,

                bx0, by0,
                bx1, by1,
                bx0, by1
        };

        glUseProgram(bgProgram);
        glUniform2f(bgUScreenLoc, screenW, screenH);
        // RGBA (black with ~50% alpha)
        glUniform4f(bgUColorLoc, 0f, 0f, 0f, 0.5f);

        glBindVertexArray(bgVao);
        glBindBuffer(GL_ARRAY_BUFFER, bgVbo);

        // Upload floats without allocations (use a temp direct buffer)
        ByteBuffer tmp = MemoryUtil.memAlloc(verts.length * Float.BYTES);
        for (float v : verts) tmp.putFloat(v);
        tmp.flip();

        glBufferSubData(GL_ARRAY_BUFFER, 0, tmp);
        MemoryUtil.memFree(tmp);

        glDrawArrays(GL_TRIANGLES, 0, 6);

        glBindVertexArray(0);
        glUseProgram(0);
    }

    @Override
    public void close() {
        glDeleteProgram(program);
        glDeleteProgram(bgProgram);

        glDeleteBuffers(vbo);
        glDeleteBuffers(ebo);
        glDeleteVertexArrays(vao);

        glDeleteBuffers(bgVbo);
        glDeleteVertexArrays(bgVao);

        MemoryUtil.memFree(vertexBuffer);
        MemoryUtil.memFree(whiteColor);
    }

    private static int compileProgram(String vsSrc, String fsSrc) {
        int vs = glCreateShader(GL_VERTEX_SHADER);
        glShaderSource(vs, vsSrc);
        glCompileShader(vs);
        if (glGetShaderi(vs, GL_COMPILE_STATUS) == GL_FALSE) {
            String log = glGetShaderInfoLog(vs);
            glDeleteShader(vs);
            throw new IllegalStateException("DebugOverlay VS compile failed:\n" + log);
        }

        int fs = glCreateShader(GL_FRAGMENT_SHADER);
        glShaderSource(fs, fsSrc);
        glCompileShader(fs);
        if (glGetShaderi(fs, GL_COMPILE_STATUS) == GL_FALSE) {
            String log = glGetShaderInfoLog(fs);
            glDeleteShader(vs);
            glDeleteShader(fs);
            throw new IllegalStateException("DebugOverlay FS compile failed:\n" + log);
        }

        int prog = glCreateProgram();
        glAttachShader(prog, vs);
        glAttachShader(prog, fs);
        glLinkProgram(prog);

        glDeleteShader(vs);
        glDeleteShader(fs);

        if (glGetProgrami(prog, GL_LINK_STATUS) == GL_FALSE) {
            String log = glGetProgramInfoLog(prog);
            glDeleteProgram(prog);
            throw new IllegalStateException("DebugOverlay program link failed:\n" + log);
        }

        return prog;
    }

    private static final String TEXT_VS = """
            #version 330 core
            layout(location=0) in vec3 aPos;
            layout(location=1) in vec4 aColor; // normalized ubyte -> 0..1

            uniform vec2 uScreen;

            out vec4 vColor;

            void main() {
                // aPos is in pixel coords, y increases downward
                float x = (aPos.x / uScreen.x) * 2.0 - 1.0;
                float y = 1.0 - (aPos.y / uScreen.y) * 2.0;
                gl_Position = vec4(x, y, 0.0, 1.0);
                vColor = aColor;
            }
            """;

    private static final String TEXT_FS = """
            #version 330 core
            in vec4 vColor;
            out vec4 FragColor;
            void main() {
                FragColor = vColor;
            }
            """;

    private static final String BG_VS = """
            #version 330 core
            layout(location=0) in vec2 aPos;

            uniform vec2 uScreen;

            void main() {
                float x = (aPos.x / uScreen.x) * 2.0 - 1.0;
                float y = 1.0 - (aPos.y / uScreen.y) * 2.0;
                gl_Position = vec4(x, y, 0.0, 1.0);
            }
            """;

    private static final String BG_FS = """
            #version 330 core
            uniform vec4 uColor;
            out vec4 FragColor;
            void main() {
                FragColor = uColor;
            }
            """;
}