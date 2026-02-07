package org.aouessar.renderer.gl;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * A simple framebuffer with a color texture and depth renderbuffer.
 * Used for reflection rendering.
 */
public final class GlFramebuffer implements AutoCloseable {

    private int fboId;
    private int colorTexId;
    private int depthRboId;
    private int width;
    private int height;

    public GlFramebuffer(int width, int height) {
        this.width = width;
        this.height = height;
        create();
    }

    private void create() {
        // Create framebuffer
        fboId = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, fboId);

        // Create color texture
        colorTexId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, colorTexId);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB, width, height, 0, GL_RGB, GL_UNSIGNED_BYTE, 0);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, colorTexId, 0);

        // Create depth renderbuffer
        depthRboId = glGenRenderbuffers();
        glBindRenderbuffer(GL_RENDERBUFFER, depthRboId);
        glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH_COMPONENT24, width, height);
        glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER, depthRboId);

        // Check completeness
        int status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
        if (status != GL_FRAMEBUFFER_COMPLETE) {
            throw new IllegalStateException("Framebuffer incomplete: " + status);
        }

        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }

    /**
     * Resize the framebuffer if dimensions changed.
     */
    public void resize(int newWidth, int newHeight) {
        if (newWidth == width && newHeight == height) return;

        // Delete old resources
        glDeleteFramebuffers(fboId);
        glDeleteTextures(colorTexId);
        glDeleteRenderbuffers(depthRboId);

        // Recreate with new size
        this.width = newWidth;
        this.height = newHeight;
        create();
    }

    /**
     * Bind this framebuffer for rendering.
     */
    public void bind() {
        glBindFramebuffer(GL_FRAMEBUFFER, fboId);
        glViewport(0, 0, width, height);
    }

    /**
     * Unbind and return to default framebuffer.
     */
    public void unbind() {
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }

    /**
     * Bind the color texture to a texture unit.
     */
    public void bindTexture(int unit) {
        glActiveTexture(GL_TEXTURE0 + unit);
        glBindTexture(GL_TEXTURE_2D, colorTexId);
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    @Override
    public void close() {
        glDeleteFramebuffers(fboId);
        glDeleteTextures(colorTexId);
        glDeleteRenderbuffers(depthRboId);
    }
}
