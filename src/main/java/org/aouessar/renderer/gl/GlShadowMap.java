package org.aouessar.renderer.gl;

import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.GL_CLAMP_TO_BORDER;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL14.GL_DEPTH_COMPONENT24;
import static org.lwjgl.opengl.GL14.GL_TEXTURE_COMPARE_FUNC;
import static org.lwjgl.opengl.GL14.GL_TEXTURE_COMPARE_MODE;
import static org.lwjgl.opengl.GL30.*;

/**
 * Depth-only framebuffer for directional sun shadows.
 * The depth texture uses hardware compare mode (sampler2DShadow) so a single
 * texture() call does the depth test with free bilinear PCF.
 */
public final class GlShadowMap implements AutoCloseable {

    public final int size;
    private final int fbo;
    private final int depthTex;

    public GlShadowMap(int size) {
        this.size = size;

        depthTex = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, depthTex);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_DEPTH_COMPONENT24, size, size, 0,
                GL_DEPTH_COMPONENT, GL_FLOAT, (ByteBuffer) null);

        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);

        // Outside the shadowed area everything counts as lit
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_BORDER);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_BORDER);
        glTexParameterfv(GL_TEXTURE_2D, GL_TEXTURE_BORDER_COLOR, new float[]{1f, 1f, 1f, 1f});

        // Hardware depth compare (sampler2DShadow)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_COMPARE_MODE, GL_COMPARE_REF_TO_TEXTURE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_COMPARE_FUNC, GL_LEQUAL);

        fbo = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, depthTex, 0);
        glDrawBuffer(GL_NONE);
        glReadBuffer(GL_NONE);

        int status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
        if (status != GL_FRAMEBUFFER_COMPLETE) {
            throw new IllegalStateException("Shadow framebuffer incomplete: " + status);
        }

        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glBindTexture(GL_TEXTURE_2D, 0);
    }

    /** Bind for depth rendering; caller must restore the viewport afterwards. */
    public void bindForWriting() {
        glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        glViewport(0, 0, size, size);
        glClear(GL_DEPTH_BUFFER_BIT);
    }

    public void unbind() {
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }

    public void bindTexture(int unit) {
        glActiveTexture(GL_TEXTURE0 + unit);
        glBindTexture(GL_TEXTURE_2D, depthTex);
    }

    @Override
    public void close() {
        glDeleteFramebuffers(fbo);
        glDeleteTextures(depthTex);
    }
}
