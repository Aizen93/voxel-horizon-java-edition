package org.aouessar.renderer.gl;

import org.aouessar.renderer.RendererConfig;
import org.joml.Matrix4f;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.GL_TEXTURE1;
import static org.lwjgl.opengl.GL13.GL_TEXTURE2;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL30.*;

/**
 * HDR post-processing pipeline:
 * <pre>
 *   scene (4x MSAA, RGBA16F + depth24)
 *     -> opaque resolve (color+depth textures: water refraction / SSR input)
 *     -> final resolve
 *     -> TAA (reproject history, 3x3 neighborhood clamp, blend)
 *     -> bright pass -> separable Gaussian bloom (half res, 2 iterations)
 *     -> screen-space god rays (half res, radial march toward the sun)
 *     -> composite to the default framebuffer with ACES filmic tonemapping
 * </pre>
 * All render targets are recreated on window resize.
 */
public final class PostProcessor implements AutoCloseable {

    private int width;
    private int height;
    private static final int SAMPLES = 4;

    // Multisampled scene target
    private int msFbo;
    private int msColorRbo;
    private int msDepthRbo;

    // Opaque-only resolve (sampled by the water shader)
    private int opaqueFbo;
    private int sceneColorTex;
    private int sceneDepthTex;

    // Final resolved scene
    private int sceneFbo;
    private int sceneTex;

    // TAA history ping-pong (full res)
    private final int[] taaFbo = new int[2];
    private final int[] taaTex = new int[2];
    private int taaIndex = 0;
    private boolean historyValid = false;

    /** What the bloom/rays/composite chain reads: TAA output, or the raw resolve. */
    private int postInputTex;

    // Bloom ping-pong (half res)
    private int bloomFboA;
    private int bloomTexA;
    private int bloomFboB;
    private int bloomTexB;

    // God rays (half res)
    private int raysFbo;
    private int raysTex;

    private final GlShaderProgram brightShader;
    private final GlShaderProgram blurShader;
    private final GlShaderProgram raysShader;
    private final GlShaderProgram taaShader;
    private final GlShaderProgram compositeShader;
    private final int vao;

    public PostProcessor(int width, int height) {
        this.vao = glGenVertexArrays();
        this.brightShader = new GlShaderProgram(RendererConfig.POST_VERT, RendererConfig.POST_BRIGHT_FRAG);
        this.blurShader = new GlShaderProgram(RendererConfig.POST_VERT, RendererConfig.POST_BLUR_FRAG);
        this.raysShader = new GlShaderProgram(RendererConfig.POST_VERT, RendererConfig.POST_RAYS_FRAG);
        this.taaShader = new GlShaderProgram(RendererConfig.POST_VERT, RendererConfig.POST_TAA_FRAG);
        this.compositeShader = new GlShaderProgram(RendererConfig.POST_VERT, RendererConfig.POST_COMPOSITE_FRAG);
        create(width, height);
    }

    public void resize(int newWidth, int newHeight) {
        if (newWidth == width && newHeight == height) return;
        destroyTargets();
        create(Math.max(1, newWidth), Math.max(1, newHeight));
    }

    private void create(int w, int h) {
        this.width = w;
        this.height = h;
        int hw = Math.max(1, w / 2);
        int hh = Math.max(1, h / 2);

        // ---- Multisampled scene ----
        msColorRbo = glGenRenderbuffers();
        glBindRenderbuffer(GL_RENDERBUFFER, msColorRbo);
        glRenderbufferStorageMultisample(GL_RENDERBUFFER, SAMPLES, GL_RGBA16F, w, h);

        msDepthRbo = glGenRenderbuffers();
        glBindRenderbuffer(GL_RENDERBUFFER, msDepthRbo);
        glRenderbufferStorageMultisample(GL_RENDERBUFFER, SAMPLES, GL_DEPTH_COMPONENT24, w, h);

        msFbo = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, msFbo);
        glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_RENDERBUFFER, msColorRbo);
        glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER, msDepthRbo);
        checkComplete("msFbo");

        // ---- Opaque resolve (color + depth textures) ----
        sceneColorTex = createColorTex(w, h);
        sceneDepthTex = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, sceneDepthTex);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_DEPTH_COMPONENT24, w, h, 0,
                GL_DEPTH_COMPONENT, GL_FLOAT, (java.nio.ByteBuffer) null);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

        opaqueFbo = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, opaqueFbo);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, sceneColorTex, 0);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, sceneDepthTex, 0);
        checkComplete("opaqueFbo");

        // ---- Final resolve ----
        sceneTex = createColorTex(w, h);
        sceneFbo = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, sceneFbo);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, sceneTex, 0);
        checkComplete("sceneFbo");

        // ---- TAA history ping-pong ----
        for (int i = 0; i < 2; i++) {
            taaTex[i] = createColorTex(w, h);
            taaFbo[i] = glGenFramebuffers();
            glBindFramebuffer(GL_FRAMEBUFFER, taaFbo[i]);
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, taaTex[i], 0);
            checkComplete("taaFbo" + i);
        }
        taaIndex = 0;
        historyValid = false;
        postInputTex = sceneTex;

        // ---- Bloom ping-pong ----
        bloomTexA = createColorTex(hw, hh);
        bloomFboA = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, bloomFboA);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, bloomTexA, 0);
        checkComplete("bloomFboA");

        bloomTexB = createColorTex(hw, hh);
        bloomFboB = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, bloomFboB);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, bloomTexB, 0);
        checkComplete("bloomFboB");

        // ---- God rays ----
        raysTex = createColorTex(hw, hh);
        raysFbo = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, raysFbo);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, raysTex, 0);
        checkComplete("raysFbo");

        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }

    private static int createColorTex(int w, int h) {
        int tex = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, tex);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA16F, w, h, 0, GL_RGBA, GL_FLOAT, (java.nio.ByteBuffer) null);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        return tex;
    }

    private static void checkComplete(String name) {
        int status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
        if (status != GL_FRAMEBUFFER_COMPLETE) {
            throw new IllegalStateException("Framebuffer incomplete (" + name + "): " + status);
        }
    }

    // -----------------------------------------------------------------
    // Frame flow
    // -----------------------------------------------------------------

    /** Bind the multisampled HDR scene target. All world rendering goes here. */
    public void beginScene() {
        glBindFramebuffer(GL_FRAMEBUFFER, msFbo);
        glViewport(0, 0, width, height);
    }

    /**
     * Resolve the opaque scene (color + depth) into textures and bind them so
     * the translucent pass can do refraction / SSR; rendering continues in the
     * multisampled scene target.
     */
    public void resolveOpaqueForWater(int colorUnit, int depthUnit) {
        glBindFramebuffer(GL_READ_FRAMEBUFFER, msFbo);
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, opaqueFbo);
        glBlitFramebuffer(0, 0, width, height, 0, 0, width, height,
                GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT, GL_NEAREST);

        glBindFramebuffer(GL_FRAMEBUFFER, msFbo);

        glActiveTexture(GL_TEXTURE0 + colorUnit);
        glBindTexture(GL_TEXTURE_2D, sceneColorTex);
        glActiveTexture(GL_TEXTURE0 + depthUnit);
        glBindTexture(GL_TEXTURE_2D, sceneDepthTex);
        glActiveTexture(GL_TEXTURE0);
    }

    /**
     * Resolve the finished scene, then blend it against the reprojected
     * history (TAA). Call after the translucent pass, before composite.
     * The matrices must be UNJITTERED view-projections.
     */
    public void resolveAndTaa(Matrix4f invViewProj, Matrix4f prevViewProj, boolean taaEnabled) {
        // Final resolve (color only)
        glBindFramebuffer(GL_READ_FRAMEBUFFER, msFbo);
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, sceneFbo);
        glBlitFramebuffer(0, 0, width, height, 0, 0, width, height,
                GL_COLOR_BUFFER_BIT, GL_NEAREST);

        if (!taaEnabled) {
            postInputTex = sceneTex;
            historyValid = false;
            return;
        }

        glDisable(GL_DEPTH_TEST);
        glDepthMask(false);
        glDisable(GL_BLEND);
        glBindVertexArray(vao);

        int dst = taaIndex;
        int src = 1 - taaIndex;

        glBindFramebuffer(GL_FRAMEBUFFER, taaFbo[dst]);
        glViewport(0, 0, width, height);
        taaShader.use();
        taaShader.setUniform1i("uScene", 0);
        taaShader.setUniform1i("uHistory", 1);
        taaShader.setUniform1i("uDepth", 2);
        taaShader.setUniformMat4("uInvViewProj", invViewProj);
        taaShader.setUniformMat4("uPrevViewProj", prevViewProj);
        taaShader.setUniform2f("uTexel", 1f / width, 1f / height);
        taaShader.setUniform1f("uBlend", historyValid ? RendererConfig.TAA_BLEND : 1.0f);

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, sceneTex);
        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D, taaTex[src]);
        glActiveTexture(GL_TEXTURE2);
        glBindTexture(GL_TEXTURE_2D, sceneDepthTex);
        glDrawArrays(GL_TRIANGLES, 0, 3);

        postInputTex = taaTex[dst];
        taaIndex = src;
        historyValid = true;

        glBindVertexArray(0);
        glActiveTexture(GL_TEXTURE0);
        glDepthMask(true);
        glEnable(GL_DEPTH_TEST);
    }

    /**
     * Run the post chain on the (TAA-resolved) scene, compositing into the
     * default framebuffer. Leaves the default framebuffer bound (for the HUD).
     *
     * @param sunU/sunV     sun position in screen UV space
     * @param sunVisible01  0 when the sun is off-screen/below horizon
     * @param underwater01  1 while the camera is below the water surface
     */
    public void composite(
            int windowW, int windowH,
            float sunU, float sunV, float sunVisible01,
            float sunR, float sunG, float sunB,
            float time, float underwater01, float lightning01
    ) {
        glDisable(GL_DEPTH_TEST);
        glDepthMask(false);
        glDisable(GL_BLEND);
        glBindVertexArray(vao);

        int hw = Math.max(1, width / 2);
        int hh = Math.max(1, height / 2);

        // ---- Bright pass -> bloomA ----
        glBindFramebuffer(GL_FRAMEBUFFER, bloomFboA);
        glViewport(0, 0, hw, hh);
        brightShader.use();
        brightShader.setUniform1i("uScene", 0);
        brightShader.setUniform1f("uThreshold", RendererConfig.BLOOM_THRESHOLD);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, postInputTex);
        glDrawArrays(GL_TRIANGLES, 0, 3);

        // ---- Gaussian blur, 2 iterations of H+V ----
        blurShader.use();
        blurShader.setUniform1i("uScene", 0);
        for (int i = 0; i < 2; i++) {
            glBindFramebuffer(GL_FRAMEBUFFER, bloomFboB);
            blurShader.setUniform2f("uDir", 1f / hw, 0f);
            glBindTexture(GL_TEXTURE_2D, bloomTexA);
            glDrawArrays(GL_TRIANGLES, 0, 3);

            glBindFramebuffer(GL_FRAMEBUFFER, bloomFboA);
            blurShader.setUniform2f("uDir", 0f, 1f / hh);
            glBindTexture(GL_TEXTURE_2D, bloomTexB);
            glDrawArrays(GL_TRIANGLES, 0, 3);
        }

        // ---- God rays ----
        glBindFramebuffer(GL_FRAMEBUFFER, raysFbo);
        raysShader.use();
        raysShader.setUniform1i("uScene", 0);
        raysShader.setUniform1i("uDepth", 1);
        raysShader.setUniform2f("uSunUv", sunU, sunV);
        raysShader.setUniform1f("uVisible", sunVisible01 * RendererConfig.GODRAY_STRENGTH);
        raysShader.setUniform1f("uAspect", (float) width / (float) height);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, postInputTex);
        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D, sceneDepthTex);
        glDrawArrays(GL_TRIANGLES, 0, 3);

        // ---- Composite + tonemap to the default framebuffer ----
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glViewport(0, 0, windowW, windowH);
        compositeShader.use();
        compositeShader.setUniform1i("uScene", 0);
        compositeShader.setUniform1i("uBloom", 1);
        compositeShader.setUniform1i("uRays", 2);
        compositeShader.setUniform1f("uExposure", RendererConfig.POST_EXPOSURE);
        compositeShader.setUniform1f("uBloomStrength", RendererConfig.BLOOM_STRENGTH);
        compositeShader.setUniform3f("uRayColor", sunR, sunG, sunB);
        compositeShader.setUniform1f("uTime", time);
        compositeShader.setUniform1f("uUnderwater", underwater01);
        compositeShader.setUniform1f("uLightning", lightning01);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, postInputTex);
        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D, bloomTexA);
        glActiveTexture(GL_TEXTURE2);
        glBindTexture(GL_TEXTURE_2D, raysTex);
        glDrawArrays(GL_TRIANGLES, 0, 3);

        glBindVertexArray(0);
        glActiveTexture(GL_TEXTURE0);
        glDepthMask(true);
        glEnable(GL_DEPTH_TEST);
    }

    private void destroyTargets() {
        glDeleteFramebuffers(new int[]{msFbo, opaqueFbo, sceneFbo, taaFbo[0], taaFbo[1], bloomFboA, bloomFboB, raysFbo});
        glDeleteRenderbuffers(new int[]{msColorRbo, msDepthRbo});
        glDeleteTextures(new int[]{sceneColorTex, sceneDepthTex, sceneTex, taaTex[0], taaTex[1], bloomTexA, bloomTexB, raysTex});
    }

    @Override
    public void close() {
        destroyTargets();
        glDeleteVertexArrays(vao);
        brightShader.close();
        blurShader.close();
        raysShader.close();
        taaShader.close();
        compositeShader.close();
    }
}
