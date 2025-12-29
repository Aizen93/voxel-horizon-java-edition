package org.aouessar.renderer;

import org.aouessar.core.api.ChunkProvider;
import org.aouessar.renderer.atlas.Atlas;
import org.aouessar.renderer.atlas.AtlasLoader;
import org.aouessar.renderer.camera.Camera;
import org.aouessar.renderer.camera.CameraController;
import org.aouessar.renderer.gl.GlMeshTiled;
import org.aouessar.renderer.gl.GlShaderProgram;
import org.aouessar.renderer.gl.GlTexture2D;
import org.aouessar.renderer.mesh.GreedyChunkMesher;
import org.aouessar.renderer.world.BlockRenderMap;
import org.aouessar.renderer.world.ChunkMeshCache;
import org.aouessar.shared.EngineConfig;
import org.joml.Matrix4f;

import org.joml.FrustumIntersection;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL.*;
import static org.lwjgl.opengl.GL11.*;

public final class LwjglRendererV1 {

    private final ChunkProvider chunkProvider;
    private final int radius; // view radius in chunks

    // Keep a little hysteresis to avoid eviction thrashing
    private final int evictRadius;

    public LwjglRendererV1(ChunkProvider chunkProvider, int radius) {
        this.chunkProvider = chunkProvider;
        this.radius = radius;
        this.evictRadius = radius + 2;
    }

    public void run() {
        if (!glfwInit()) throw new IllegalStateException("Failed to init GLFW");

        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);

        long window = glfwCreateWindow(RendererConfig.WINDOW_WIDTH, RendererConfig.WINDOW_HEIGHT, RendererConfig.WINDOW_TITLE, 0, 0);
        if (window == 0) throw new IllegalStateException("Failed to create GLFW window");

        glfwMakeContextCurrent(window);
        glfwSwapInterval(0);
        createCapabilities();

        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);
        glFrontFace(GL_CCW);

        glEnable(GL_DEPTH_TEST);
        glClearColor(RendererConfig.FOGR, RendererConfig.FOGG, RendererConfig.FOGB, 1.0f);

        Atlas atlas = new AtlasLoader().loadFromResources(RendererConfig.ATLAS_JSON);
        BlockRenderMap brm = new BlockRenderMap();

        try (
                GlShaderProgram shader = new GlShaderProgram(RendererConfig.VOXEL_TILED_VERT, RendererConfig.VOXEL_TILED_FRAG);
                GlShaderProgram shaderTranslucent = new GlShaderProgram(RendererConfig.VOXEL_TILED_VERT, RendererConfig.VOXEL_TILED_TRANSLUCENT_FRAG);
                GlShaderProgram shaderCutout = new GlShaderProgram(RendererConfig.VOXEL_TILED_VERT, RendererConfig.VOXEL_TILED_CUTOUT_FRAG);
                GlTexture2D atlasTex = new GlTexture2D(RendererConfig.ATLAS_PNG);

                // 3 caches: opaque, cutout, translucent
                ChunkMeshCache opaqueCache = new ChunkMeshCache(Math.max(1, Runtime.getRuntime().availableProcessors() - 1), 128, GlMeshTiled::new);
                ChunkMeshCache cutoutCache = new ChunkMeshCache(Math.max(1, Runtime.getRuntime().availableProcessors() - 1), 128, GlMeshTiled::new);
                ChunkMeshCache translucentCache = new ChunkMeshCache(Math.max(1, Runtime.getRuntime().availableProcessors() - 1), 128, GlMeshTiled::new)
        ) {
            // Common uniforms
            setupShaderCommonUniforms(shader);
            setupShaderCommonUniforms(shaderCutout);
            setupShaderCommonUniforms(shaderTranslucent);

            Camera camera = new Camera();
            CameraController controller = new CameraController(camera, window);

            GreedyChunkMesher mesher = new GreedyChunkMesher();

            double lastTime = glfwGetTime();

            int frames = 0;
            double acc = 0.0;

            while (!glfwWindowShouldClose(window)) {
                double now = glfwGetTime();
                float dt = (float) (now - lastTime);

                glfwPollEvents();
                controller.update(dt);

                int[] width = new int[1];
                int[] height = new int[1];
                glfwGetFramebufferSize(window, width, height);
                glViewport(0, 0, width[0], height[0]);

                glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

                // Camera -> chunk center
                int centerCx = (int) Math.floor(camera.position.x / EngineConfig.CHUNK_SIZE);
                int centerCz = (int) Math.floor(camera.position.z / EngineConfig.CHUNK_SIZE);

                // ---- REQUEST (build once, submit to 3 caches) ----
                // We must not call mesher 3 times per chunk.
                // So: build ChunkMeshes once and fan-out results.
                // Easiest with current cache API: request on opaque cache, and in supplier also enqueue into others.
                // (We rely on the fact that requestRadius won't call supplier for already-cached chunks.)
                opaqueCache.requestRadius(centerCx, centerCz, radius, RendererConfig.SUBMIT_BUDGET_PER_FRAME, (mx, mz) -> {
                    var meshes = mesher.buildChunkMeshes(chunkProvider, atlas, brm, mx, mz);

                    // Side-enqueue into other caches
                    cutoutCache.requestOne(mx, mz, meshes.cutout());
                    translucentCache.requestOne(mx, mz, meshes.translucent());

                    return meshes.opaque();
                });

                // Ensure other caches also request around radius for eviction bookkeeping
                cutoutCache.touchRadius(centerCx, centerCz, radius);
                translucentCache.touchRadius(centerCx, centerCz, radius);

                // ---- UPLOAD ----
                opaqueCache.uploadReady(RendererConfig.UPLOAD_BUDGET_PER_FRAME);
                cutoutCache.uploadReady(RendererConfig.UPLOAD_BUDGET_PER_FRAME);
                translucentCache.uploadReady(RendererConfig.UPLOAD_BUDGET_PER_FRAME);

                // ---- EVICT ----
                opaqueCache.evictOutside(centerCx, centerCz, evictRadius);
                cutoutCache.evictOutside(centerCx, centerCz, evictRadius);
                translucentCache.evictOutside(centerCx, centerCz, evictRadius);

                // MVP
                Matrix4f proj = new Matrix4f()
                    .perspective(
                        (float) Math.toRadians(75),
                        (float) width[0] / (float) height[0],
                        0.1f,
                        8000f
                );

                Matrix4f view = camera.viewMatrix();
                Matrix4f mvp = new Matrix4f(proj).mul(view);
                FrustumIntersection frustum = new FrustumIntersection(mvp);

                // World vertical bounds — MUST match core EngineConfig
                final float minY = EngineConfig.MIN_Y;
                final float maxY = EngineConfig.MAX_Y + 1f;

                atlasTex.bind(0);

                // ----------------------------
                // PASS 1: OPAQUE
                // ----------------------------
                glEnable(GL_DEPTH_TEST);
                glDepthMask(true);
                glDisable(GL_BLEND);

                setupShaderPassUniforms(shader, camera, mvp);
                //opaqueCache.drawAll();
                int drawnOpaque = opaqueCache.drawAllCulled(frustum, EngineConfig.CHUNK_SIZE, minY, maxY);

                // ----------------------------
                // PASS 2: CUTOUT (alpha discard)
                // ----------------------------
                glEnable(GL_DEPTH_TEST);
                glDepthMask(true);
                glDisable(GL_BLEND);

                setupShaderPassUniforms(shaderCutout, camera, mvp);
                //cutoutCache.drawAll();
                int drawnCutout = cutoutCache.drawAllCulled(frustum, EngineConfig.CHUNK_SIZE, minY, maxY);


                // ----------------------------
                // PASS 3: TRANSLUCENT (water/glass)
                // ----------------------------
                glEnable(GL_DEPTH_TEST);
                glDepthMask(false); // don't write depth for blending
                glEnable(GL_BLEND);
                glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

                setupShaderPassUniforms(shaderTranslucent, camera, mvp);
                shaderTranslucent.setUniform1f("uTime", (float) now);

                float camChunkX = camera.position.x / EngineConfig.CHUNK_SIZE;
                float camChunkZ = camera.position.z / EngineConfig.CHUNK_SIZE;
                //translucentCache.drawSorted(camChunkX, camChunkZ);
                int drawnTrans = translucentCache.drawSortedCulled(camChunkX, camChunkZ, frustum, EngineConfig.CHUNK_SIZE, minY, maxY);

                glDisable(GL_BLEND);
                glDepthMask(true);

                lastTime = now;

                // FPS
                acc += dt;
                frames++;
                if (acc >= 1.0) {
                    glfwSetWindowTitle(window,
                    "Voxel Renderer v1 (r = " + radius +
                        ", FPS: " + frames +
                        ", opaque: [Drawn=" + drawnOpaque + ", Count=" + opaqueCache.meshCount() + "]" +
                        ", cutout: [Drawn=" + drawnCutout + ", Count=" + cutoutCache.meshCount() + "]" +
                        ", trans: [Drawn=" + drawnTrans + ", Count=" + translucentCache.meshCount() + "]" +
                        ", inFlight = " + (opaqueCache.inFlightCount() + cutoutCache.inFlightCount() + translucentCache.inFlightCount()) + ")"
                    );
                    frames = 0;
                    acc = 0.0;
                }

                glfwSwapBuffers(window);
            }
        } finally {
            glfwDestroyWindow(window);
            glfwTerminate();
        }
    }

    private void setupShaderCommonUniforms(GlShaderProgram shader) {
        shader.use();
        shader.setUniform1i("uAtlas", 0);
        shader.setUniform2f("uTileSize", (RendererConfig.ATLAS_TILE_SIZE / RendererConfig.ATLAS_WIDTH), (RendererConfig.ATLAS_TILE_SIZE / RendererConfig.ATLAS_HEIGHT));
        shader.setUniform3f("uFogColor", RendererConfig.FOGR, RendererConfig.FOGG, RendererConfig.FOGB);
        shader.setUniform1f("uFogAltBase", RendererConfig.FOG_ALT_BASE);
        shader.setUniform1f("uFogAltRange", RendererConfig.FOG_ALT_RANGE);
        shader.setUniform1f("uFogStartLow", RendererConfig.FOG_START_LOW);
        shader.setUniform1f("uFogStartHigh", RendererConfig.FOG_START_HIGH);
        shader.setUniform1f("uFogRangeLow", RendererConfig.FOG_RANGE_LOW);
        shader.setUniform1f("uFogRangeHigh", RendererConfig.FOG_RANGE_HIGH);
    }

    private void setupShaderPassUniforms(GlShaderProgram shader, Camera camera, Matrix4f mvp) {
        // Fog needs camera world position every frame
        float camX = camera.position.x;
        float camY = camera.position.y;
        float camZ = camera.position.z;

        shader.use();
        shader.setUniformMat4("uMVP", mvp);
        shader.setUniform3f("uCameraPos", camX, camY, camZ);
    }
}