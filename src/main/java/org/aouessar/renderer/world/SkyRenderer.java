package org.aouessar.renderer.world;

import org.aouessar.renderer.RendererConfig;
import org.aouessar.renderer.gl.GlShaderProgram;
import org.joml.Math;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL30.*;

public final class SkyRenderer implements AutoCloseable {

    private final GlShaderProgram skyShader;
    private final int vao;

    public SkyRenderer(String vertPath, String fragPath) {
        this.skyShader = new GlShaderProgram(vertPath, fragPath);
        this.vao = glGenVertexArrays();
    }

    public void render(FogCycle fog) {
        float tw = Math.clamp(fog.twilight01(), 0f, 1f);

        // Top sky = fog color boosted a bit at twilight
        float boost = 1.0f + tw * RendererConfig.SKY_TWILIGHT_BOOST;
        float topR = Math.clamp(fog.r() * boost, 0f, 1f);
        float topG = Math.clamp(fog.g() * boost, 0f, 1f);
        float topB = Math.clamp(fog.b() * boost, 0f, 1f);

        // Horizon: mostly fog color, warmed only at twilight
        // or tw*tw
        float horR = Math.lerp(fog.r(), RendererConfig.FOG_WARM_R, tw);
        float horG = Math.lerp(fog.g(), RendererConfig.FOG_WARM_G, tw);
        float horB = Math.lerp(fog.b(), RendererConfig.FOG_WARM_B, tw);

        // Clear is optional; triangle covers screen anyway
        glClearColor(0f, 0f, 0f, 1f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        glDisable(GL_DEPTH_TEST);
        glDepthMask(false);

        skyShader.use();
        skyShader.setUniform3f("uSkyTopColor", topR, topG, topB);
        skyShader.setUniform3f("uSkyHorizonColor", horR, horG, horB);
        skyShader.setUniform1f("uTwilight01", tw);
        skyShader.setUniform1f("uHorizonStrength", RendererConfig.SKY_HORIZON_STRENGTH);
        skyShader.setUniform1f("uHorizonY", RendererConfig.SKY_HORIZON_Y);
        skyShader.setUniform1f("uHorizonSoftness", RendererConfig.SKY_HORIZON_SOFTNESS);

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