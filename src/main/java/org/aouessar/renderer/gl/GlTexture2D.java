package org.aouessar.renderer.gl;

import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.*;
import static org.lwjgl.opengl.GL13.*;

public final class GlTexture2D implements AutoCloseable {

    private final int textureId;
    public final int width;
    public final int height;

    public GlTexture2D(String resourcePath) {
        Image img = loadImage(resourcePath);
        this.width = img.w;
        this.height = img.h;

        textureId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, textureId);

        // NEAREST for crisp pixels
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);

        // Clamp is safer for atlas edges (with padding you can switch to REPEAT later if desired)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, img.pixels);
        glBindTexture(GL_TEXTURE_2D, 0);

        STBImage.stbi_image_free(img.pixels);
    }

    public void bind(int unit) {
        glActiveTexture(GL_TEXTURE0 + unit);
        glBindTexture(GL_TEXTURE_2D, textureId);
    }

    @Override
    public void close() {
        glBindTexture(GL_TEXTURE_2D, 0);
        glDeleteTextures(textureId);
    }

    private record Image(ByteBuffer pixels, int w, int h) {}

    private static Image loadImage(String resourcePath) {
        try {
            byte[] bytes;
            try (InputStream in = GlTexture2D.class.getResourceAsStream(resourcePath)) {
                if (in == null) throw new IllegalArgumentException("Resource not found: " + resourcePath);
                bytes = in.readAllBytes();
            }

            ByteBuffer data = ByteBuffer.allocateDirect(bytes.length);
            data.put(bytes).flip();

            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer w = stack.mallocInt(1);
                IntBuffer h = stack.mallocInt(1);
                IntBuffer comp = stack.mallocInt(1);

                STBImage.stbi_set_flip_vertically_on_load(false);
                ByteBuffer pixels = STBImage.stbi_load_from_memory(data, w, h, comp, 4);
                if (pixels == null) {
                    throw new IllegalStateException("STB failed: " + STBImage.stbi_failure_reason());
                }
                return new Image(pixels, w.get(0), h.get(0));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load image: " + resourcePath, e);
        }
    }
}