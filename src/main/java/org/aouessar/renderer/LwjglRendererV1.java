package org.aouessar.renderer;

import org.aouessar.core.api.WorldAccess;
import org.aouessar.core.stream.RegionStreamingService;
import org.aouessar.renderer.atlas.Atlas;
import org.aouessar.renderer.atlas.AtlasLoader;
import org.aouessar.renderer.camera.Camera;
import org.aouessar.renderer.camera.CameraController;
import org.aouessar.renderer.gl.GlMeshTiled;
import org.aouessar.renderer.gl.GlShaderProgram;
import org.aouessar.renderer.gl.GlTexture2D;
import org.aouessar.renderer.mesh.GreedyChunkMesher;
import org.aouessar.renderer.ui.DebugOverlay;
import org.aouessar.renderer.world.*;
import org.aouessar.shared.EngineConfig;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.joml.Math;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL.createCapabilities;
import static org.lwjgl.opengl.GL11.*;

public final class LwjglRendererV1 {

    private final WorldAccess world;
    private final int radius; // view radius in chunks

    // Keep a little hysteresis to avoid eviction thrashing
    private final int evictRadius;
    private final FogCycle fogCycle = new FogCycle();
    private final LongKeyList visibleKeys = new LongKeyList(8192);

    public LwjglRendererV1(WorldAccess world, int radius) {
        this.world = world;
        this.radius = radius;
        this.evictRadius = radius + 2;
    }

    public void run() {
        if (!glfwInit()) throw new IllegalStateException("Failed to init GLFW");

        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);

        long window = glfwCreateWindow(
                RendererConfig.WINDOW_WIDTH,
                RendererConfig.WINDOW_HEIGHT,
                RendererConfig.WINDOW_TITLE,
                0,
                0
        );
        if (window == 0) throw new IllegalStateException("Failed to create GLFW window");

        glfwMakeContextCurrent(window);
        glfwSwapInterval(0);
        createCapabilities();

        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);
        glFrontFace(GL_CCW);
        glEnable(GL_DEPTH_TEST);

        // --------------------------------------------------
        // Framebuffer size (cached, no per-frame allocation)
        // --------------------------------------------------
        final int[] fbW = new int[1];
        final int[] fbH = new int[1];
        glfwGetFramebufferSize(window, fbW, fbH);
        glViewport(0, 0, fbW[0], fbH[0]);

        // --------------------------------------------------
        // Projection & frustum (cached)
        // --------------------------------------------------
        final Matrix4f proj = new Matrix4f();
        final Matrix4f view = new Matrix4f();
        final Matrix4f mvp  = new Matrix4f();
        final FrustumIntersection frustum = new FrustumIntersection();

        Runnable updateProjection = () -> proj.identity().perspective(
                (float) Math.toRadians(75.0),
                (float) fbW[0] / (float) fbH[0],
                0.1f,
                8000f
        );
        updateProjection.run();

        glfwSetFramebufferSizeCallback(window, (win, w, h) -> {
            fbW[0] = Math.max(1, w);
            fbH[0] = Math.max(1, h);
            glViewport(0, 0, fbW[0], fbH[0]);
            updateProjection.run();
        });

        Atlas atlas = new AtlasLoader().loadFromResources(RendererConfig.ATLAS_JSON);
        BlockRenderMap brm = new BlockRenderMap();

        // Debug HUD text: update once per second, render every frame
        final StringBuilder debug = new StringBuilder(2048);

        try (
                // NOW it's safe: OpenGL context + capabilities are live.
                DebugOverlay hud = new DebugOverlay();

                SkyRenderer sky = new SkyRenderer(RendererConfig.SKY_VERT, RendererConfig.SKY_FRAG);

                GlShaderProgram shader = new GlShaderProgram(
                        RendererConfig.VOXEL_TILED_VERT,
                        RendererConfig.VOXEL_TILED_FRAG
                );
                GlShaderProgram shaderCutout = new GlShaderProgram(
                        RendererConfig.VOXEL_TILED_VERT,
                        RendererConfig.VOXEL_TILED_CUTOUT_FRAG
                );
                GlShaderProgram shaderTranslucent = new GlShaderProgram(
                        RendererConfig.VOXEL_TILED_VERT,
                        RendererConfig.VOXEL_TILED_TRANSLUCENT_FRAG
                );
                GlTexture2D atlasTex = new GlTexture2D(RendererConfig.ATLAS_PNG);

                ChunkMeshCache opaqueCache = new ChunkMeshCache(
                        Math.max(1, Runtime.getRuntime().availableProcessors() - 1),
                        128,
                        GlMeshTiled::new
                );
                ChunkMeshCache cutoutCache = new ChunkMeshCache(
                        Math.max(1, Runtime.getRuntime().availableProcessors() - 1),
                        128,
                        GlMeshTiled::new
                );
                ChunkMeshCache translucentCache = new ChunkMeshCache(
                        Math.max(1, Runtime.getRuntime().availableProcessors() - 1),
                        128,
                        GlMeshTiled::new
                )
        ) {
            // Static uniforms
            setupShaderCommonUniforms(shader);
            setupShaderCommonUniforms(shaderCutout);
            setupShaderCommonUniforms(shaderTranslucent);

            Camera camera = new Camera();
            CameraController controller = new CameraController(camera, window, world.biomeLocator());
            GreedyChunkMesher mesher = new GreedyChunkMesher();

            double lastTime = glfwGetTime();
            double acc = 0.0;
            int frames = 0;

            while (!glfwWindowShouldClose(window)) {
                double now = glfwGetTime();
                float dt = (float) (now - lastTime);
                lastTime = now;

                glfwPollEvents();
                controller.update(dt);

                // -----------------------------
                // Fog + sky
                // -----------------------------
                fogCycle.setRain01(RendererConfig.DEBUG_RAIN);
                fogCycle.setMist01(RendererConfig.DEBUG_MIST);
                fogCycle.update((float) now, camera.position.y);
                sky.render(fogCycle);

                // -----------------------------
                // Camera & matrices
                // -----------------------------
                view.set(camera.viewMatrix());
                mvp.set(proj).mul(view);
                frustum.set(mvp);

                int centerCx = (int) Math.floor(camera.position.x / EngineConfig.CHUNK_SIZE);
                int centerCz = (int) Math.floor(camera.position.z / EngineConfig.CHUNK_SIZE);

                float minY = EngineConfig.MIN_Y;
                float maxY = EngineConfig.MAX_Y + 1f;

                // -----------------------------
                // REQUEST
                // -----------------------------
                opaqueCache.requestRadiusCulled(
                    centerCx,
                    centerCz,
                    radius,
                    RendererConfig.SUBMIT_BUDGET_PER_FRAME,
                    (mx, mz) -> {
                        var meshes = mesher.buildChunkMeshes(world.chunkProvider(), atlas, brm, mx, mz);
                        cutoutCache.requestOne(mx, mz, meshes.cutout());
                        translucentCache.requestOne(mx, mz, meshes.translucent());
                        return meshes.opaque();
                    },
                    frustum,
                    EngineConfig.CHUNK_SIZE,
                    minY,
                    maxY
                );

                cutoutCache.touchRadius(centerCx, centerCz, radius);
                translucentCache.touchRadius(centerCx, centerCz, radius);

                // -----------------------------
                // UPLOAD
                // -----------------------------
                opaqueCache.uploadReady(RendererConfig.UPLOAD_BUDGET_PER_FRAME);
                cutoutCache.uploadReady(RendererConfig.UPLOAD_BUDGET_PER_FRAME);
                translucentCache.uploadReady(RendererConfig.UPLOAD_BUDGET_PER_FRAME);

                // -----------------------------
                // VISIBLE SET (once)
                // -----------------------------
                visibleKeys.clear();
                opaqueCache.collectVisibleKeys(
                    visibleKeys,
                    frustum,
                    EngineConfig.CHUNK_SIZE,
                    minY,
                    maxY
                );

                // -----------------------------
                // DRAW
                // -----------------------------
                atlasTex.bind(0);

                // ---- PASS 1: OPAQUE ----
                glEnable(GL_DEPTH_TEST);
                glDepthMask(true);
                glDisable(GL_BLEND);

                applyPerFrameUniforms(shader, camera, mvp, fogCycle);
                int drawnOpaque = opaqueCache.drawKeys(visibleKeys);

                // ---- PASS 2: CUTOUT ----
                applyPerFrameUniforms(shaderCutout, camera, mvp, fogCycle);
                int drawnCutout = cutoutCache.drawKeys(visibleKeys);

                // ---- PASS 3: TRANSLUCENT ----
                glDepthMask(false);
                glEnable(GL_BLEND);
                glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

                applyPerFrameUniforms(shaderTranslucent, camera, mvp, fogCycle);
                shaderTranslucent.setUniform1f("uTime", (float) now);

                float camChunkX = camera.position.x / EngineConfig.CHUNK_SIZE;
                float camChunkZ = camera.position.z / EngineConfig.CHUNK_SIZE;

                int drawnTrans = translucentCache.drawKeysSorted(
                    visibleKeys,
                    camChunkX,
                    camChunkZ
                );

                glDisable(GL_BLEND);
                glDepthMask(true);

                // -----------------------------
                // EVICT
                // -----------------------------
                opaqueCache.evictOutside(centerCx, centerCz, evictRadius);
                cutoutCache.evictOutside(centerCx, centerCz, evictRadius);
                translucentCache.evictOutside(centerCx, centerCz, evictRadius);

                // -----------------------------
                // HUD text (update once/sec)
                // -----------------------------
                acc += dt;
                frames++;
                if (acc >= 1.0) {
                    debug.setLength(0);

                    debug.append("FPS: ").append(frames).append('\n');
                    debug.append("radius: ").append(radius).append('\n');

                    debug.append("Opaque:  D=").append(drawnOpaque)
                            .append(" M=").append(opaqueCache.meshCount())
                            .append(" E=").append(opaqueCache.entryCount())
                            .append(" R=").append(opaqueCache.readyCount())
                            .append(" F=").append(opaqueCache.inFlightCount()).append('\n');

                    debug.append("Cutout:  D=").append(drawnCutout)
                            .append(" M=").append(cutoutCache.meshCount())
                            .append(" E=").append(cutoutCache.entryCount())
                            .append(" R=").append(cutoutCache.readyCount())
                            .append(" F=").append(cutoutCache.inFlightCount()).append('\n');

                    debug.append("Trans:   D=").append(drawnTrans)
                            .append(" M=").append(translucentCache.meshCount())
                            .append(" E=").append(translucentCache.entryCount())
                            .append(" R=").append(translucentCache.readyCount())
                            .append(" F=").append(translucentCache.inFlightCount()).append('\n');

                    long usedMb = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024 * 1024);
                    long totalMb = Runtime.getRuntime().totalMemory() / (1024 * 1024);
                    debug.append("Heap: ").append(usedMb).append(" / ").append(totalMb).append(" MB\n");

                    debug.append("Pos: ")
                            .append(camera.position.x).append(", ")
                            .append(camera.position.y).append(", ")
                            .append(camera.position.z).append('\n');

                    acc = 0.0;
                    frames = 0;
                }

                // -----------------------------
                // HUD render (every frame)
                // -----------------------------
                hud.render(fbW[0], fbH[0], 8f, 8f, debug.toString());

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
        shader.setUniform2f("uTileSize",
                (RendererConfig.ATLAS_TILE_SIZE / RendererConfig.ATLAS_WIDTH),
                (RendererConfig.ATLAS_TILE_SIZE / RendererConfig.ATLAS_HEIGHT)
        );

        // Fog constants (rarely change)
        shader.setUniform1f("uFogAltBase", RendererConfig.FOG_ALT_BASE);
        shader.setUniform1f("uFogAltRange", RendererConfig.FOG_ALT_RANGE);
        shader.setUniform1f("uFogStartLow", RendererConfig.FOG_START_LOW);
        shader.setUniform1f("uFogStartHigh", RendererConfig.FOG_START_HIGH);
        shader.setUniform1f("uFogRangeLow", RendererConfig.FOG_RANGE_LOW);
        shader.setUniform1f("uFogRangeHigh", RendererConfig.FOG_RANGE_HIGH);

        // Safe defaults
        shader.setUniform1f("uFogStartMul", 1.0f);
        shader.setUniform1f("uFogRangeMul", 1.0f);
    }

    private void applyPerFrameUniforms(GlShaderProgram shader, Camera camera, Matrix4f mvp, FogCycle fog) {
        shader.use();
        shader.setUniformMat4("uMVP", mvp);

        shader.setUniform3f("uCameraPos", camera.position.x, camera.position.y, camera.position.z);

        shader.setUniform3f("uFogColor", fog.r(), fog.g(), fog.b());
        shader.setUniform1f("uFogStartMul", fog.startMul());
        shader.setUniform1f("uFogRangeMul", fog.rangeMul());
    }
}