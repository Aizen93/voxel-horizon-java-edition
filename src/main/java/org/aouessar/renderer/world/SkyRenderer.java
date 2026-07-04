package org.aouessar.renderer.world;

import org.aouessar.renderer.RendererConfig;
import org.aouessar.renderer.gl.GlShaderProgram;
import org.joml.Math;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * Directional sky: gradient + sun disc + animated procedural cloud layer.
 * Also clears the framebuffer (the fullscreen triangle covers everything).
 */
public final class SkyRenderer implements AutoCloseable {

    private final GlShaderProgram skyShader;
    private final int vao;

    public SkyRenderer(String vertPath, String fragPath) {
        this.skyShader = new GlShaderProgram(vertPath, fragPath);
        this.vao = glGenVertexArrays();
    }

    public void render(FogCycle fog, Matrix4f invProj, Matrix4f invViewRot, Vector3f cameraPos, float time,
                       float underwater01, float uwR, float uwG, float uwB) {
        float tw = Math.clamp(0f, 1f, fog.twilight01());

        // Top sky = fog color boosted a bit at twilight
        float boost = 1.0f + tw * RendererConfig.SKY_TWILIGHT_BOOST;
        float topR = Math.clamp(0f, 1f, fog.skyTopR() * boost);
        float topG = Math.clamp(0f, 1f, fog.skyTopG() * boost);
        float topB = Math.clamp(0f, 1f, fog.skyTopB() * boost);

        glClearColor(0f, 0f, 0f, 1f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        glDisable(GL_DEPTH_TEST);
        glDepthMask(false);

        skyShader.use();
        skyShader.setUniformMat4("uInvProj", invProj);
        skyShader.setUniformMat4("uInvViewRot", invViewRot);
        skyShader.setUniform3f("uCameraPos", cameraPos.x, cameraPos.y, cameraPos.z);
        skyShader.setUniform3f("uSunDir", fog.sunDirX(), fog.sunDirY(), fog.sunDirZ());
        skyShader.setUniform3f("uSkyTopColor", topR, topG, topB);
        skyShader.setUniform3f("uSkyHorizonColor", fog.skyHorizonR(), fog.skyHorizonG(), fog.skyHorizonB());
        skyShader.setUniform1f("uTwilight01", tw);
        skyShader.setUniform1f("uTime", time);
        skyShader.setUniform1f("uCloudCover", RendererConfig.CLOUD_COVER);
        skyShader.setUniform1f("uCloudHeight", RendererConfig.CLOUD_HEIGHT);
        skyShader.setUniform1f("uUnderwater", underwater01);
        skyShader.setUniform3f("uUnderwaterColor", uwR, uwG, uwB);

        glBindVertexArray(vao);
        glDrawArrays(GL_TRIANGLES, 0, 3);
        glBindVertexArray(0);

        glDepthMask(true);
        glEnable(GL_DEPTH_TEST);
    }

    @Override
    public void close() {
        glDeleteVertexArrays(vao);
        skyShader.close();
    }
}
