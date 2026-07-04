package org.aouessar.renderer;

import org.aouessar.core.api.WorldAccess;
import org.aouessar.renderer.atlas.Atlas;
import org.aouessar.renderer.atlas.AtlasLoader;
import org.aouessar.renderer.camera.Camera;
import org.aouessar.renderer.camera.CameraController;
import org.aouessar.renderer.gl.GlMeshTiled;
import org.aouessar.renderer.gl.GlShaderProgram;
import org.aouessar.renderer.gl.GlShadowMap;
import org.aouessar.renderer.gl.GlTexture2D;
import org.aouessar.renderer.mesh.GreedyChunkMesher;
import org.aouessar.renderer.mesh.LodMesher;
import org.aouessar.renderer.ui.DebugOverlay;
import org.aouessar.renderer.world.*;
import org.aouessar.shared.EngineConfig;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.joml.Math;
import org.joml.Vector3f;

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

    // Eviction throttling - don't evict every frame
    private int evictCounter = 0;

    // TAA jitter sequence (Halton base 2 / base 3), in pixel units
    private static final float[] HALTON2 = {0.5f, 0.25f, 0.75f, 0.125f, 0.625f, 0.375f, 0.875f, 0.0625f};
    private static final float[] HALTON3 = {1f / 3f, 2f / 3f, 1f / 9f, 4f / 9f, 7f / 9f, 2f / 9f, 5f / 9f, 8f / 9f};

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
        glfwWindowHint(GLFW_SAMPLES, 4); // 4x MSAA: smooth block/LOD edges

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
        glEnable(org.lwjgl.opengl.GL13.GL_MULTISAMPLE);

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
        final Matrix4f proj = new Matrix4f();      // unjittered projection
        final Matrix4f projJit = new Matrix4f();   // TAA-jittered projection
        final Matrix4f view = new Matrix4f();
        final Matrix4f mvp  = new Matrix4f();      // rendering (jittered when TAA is on)
        final Matrix4f mvpNoJit = new Matrix4f();  // sun screen pos + TAA reprojection
        final Matrix4f invViewProj = new Matrix4f();
        final Matrix4f prevViewProj = new Matrix4f();
        final FrustumIntersection frustum = new FrustumIntersection();

        // Cascaded sun shadow matrices (rebuilt per frame while the sun is up)
        final int cascades = RendererConfig.SHADOW_CASCADE_EXTENTS.length;
        final Matrix4f[] lightMVPs = new Matrix4f[cascades];
        for (int i = 0; i < cascades; i++) lightMVPs[i] = new Matrix4f();
        final Matrix4f lightView = new Matrix4f();
        final Matrix4f lightProj = new Matrix4f();
        final Vector3f lightTmp  = new Vector3f();

        // Per-cascade split distances + NDC depth bias (constants of the setup)
        final float[] cascSplits = new float[cascades];
        final float[] cascBias = new float[cascades];
        for (int i = 0; i < cascades; i++) {
            float ext = RendererConfig.SHADOW_CASCADE_EXTENTS[i];
            float zRange = ext + 500f;
            float texelWorld = (2f * ext) / RendererConfig.SHADOW_MAP_SIZE;
            cascSplits[i] = ext * ((i == cascades - 1) ? 0.95f : 0.90f);
            cascBias[i] = java.lang.Math.max(1.8f, 2.6f * texelWorld) / (2f * zRange);
        }

        // Sky view-ray reconstruction
        final Matrix4f invProj = new Matrix4f();
        final Matrix4f invViewRot = new Matrix4f();

        // Sun screen position for god rays
        final org.joml.Vector4f sunClip = new org.joml.Vector4f();

        Runnable updateProjection = () -> proj.identity().perspective(
                (float) Math.toRadians(75.0),
                (float) fbW[0] / (float) fbH[0],
                RendererConfig.CAMERA_NEAR,
                RendererConfig.CAMERA_FAR
        );
        updateProjection.run();

        final boolean[] fbResized = new boolean[1];
        glfwSetFramebufferSizeCallback(window, (win, w, h) -> {
            fbW[0] = Math.max(1, w);
            fbH[0] = Math.max(1, h);
            glViewport(0, 0, fbW[0], fbH[0]);
            updateProjection.run();
            fbResized[0] = true;
        });

        Atlas atlas = new AtlasLoader().loadFromResources(RendererConfig.ATLAS_JSON);
        BlockRenderMap brm = new BlockRenderMap();

        // Far-field LOD: vertex colors derived from the atlas so distant
        // terrain matches near-field textures exactly.
        AtlasColorMap atlasColors = new AtlasColorMap(RendererConfig.ATLAS_PNG, atlas, brm);
        LodMesher lodMesher = new LodMesher(atlasColors);

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
                GlShaderProgram shaderLodTerrain = new GlShaderProgram(
                        RendererConfig.LOD_TERRAIN_VERT,
                        RendererConfig.LOD_TERRAIN_FRAG
                );
                GlShaderProgram shaderLodWater = new GlShaderProgram(
                        RendererConfig.LOD_TERRAIN_VERT,
                        RendererConfig.LOD_WATER_FRAG
                );
                GlShaderProgram shaderShadowDepth = new GlShaderProgram(
                        RendererConfig.SHADOW_VERT,
                        RendererConfig.SHADOW_FRAG
                );
                GlShaderProgram shaderLodShadow = new GlShaderProgram(
                        RendererConfig.LOD_SHADOW_VERT,
                        RendererConfig.LOD_SHADOW_FRAG
                );
                GlShadowMap shadowMap0 = new GlShadowMap(RendererConfig.SHADOW_MAP_SIZE);
                GlShadowMap shadowMap1 = new GlShadowMap(RendererConfig.SHADOW_MAP_SIZE);
                GlShadowMap shadowMap2 = new GlShadowMap(RendererConfig.SHADOW_MAP_SIZE);
                org.aouessar.renderer.gl.PostProcessor post =
                        new org.aouessar.renderer.gl.PostProcessor(fbW[0], fbH[0]);
                GlTexture2D atlasTex = new GlTexture2D(RendererConfig.ATLAS_PNG);

                ChunkMeshCache opaqueCache = new ChunkMeshCache(
                        Math.max(1, Runtime.getRuntime().availableProcessors() - 1),
                        RendererConfig.MAX_IN_FLIGHT_MESHES,
                        GlMeshTiled::new
                );
                ChunkMeshCache cutoutCache = new ChunkMeshCache(
                        Math.max(1, Runtime.getRuntime().availableProcessors() - 1),
                        RendererConfig.MAX_IN_FLIGHT_MESHES,
                        GlMeshTiled::new
                );
                ChunkMeshCache translucentCache = new ChunkMeshCache(
                        Math.max(1, Runtime.getRuntime().availableProcessors() - 1),
                        RendererConfig.MAX_IN_FLIGHT_MESHES,
                        GlMeshTiled::new
                );
                LodMeshCache lodCache = new LodMeshCache(
                        RendererConfig.LOD_WORKER_THREADS,
                        RendererConfig.LOD_MAX_IN_FLIGHT
                )
        ) {
            final GlShadowMap[] shadowMaps = {shadowMap0, shadowMap1, shadowMap2};

            // Static uniforms
            setupShaderCommonUniforms(shader);
            setupShaderCommonUniforms(shaderCutout);
            // Water v3 composites the refracted scene; it no longer samples the atlas
            setupFogUniforms(shaderTranslucent);
            setupFogUniforms(shaderLodTerrain);
            setupFogUniforms(shaderLodWater);

            shaderShadowDepth.use();
            shaderShadowDepth.setUniform1i("uAtlas", 0);
            shaderShadowDepth.setUniform2f("uTileSize",
                    (RendererConfig.ATLAS_TILE_SIZE / RendererConfig.ATLAS_WIDTH),
                    (RendererConfig.ATLAS_TILE_SIZE / RendererConfig.ATLAS_HEIGHT));

            // Shadow cascade samplers: units 1 / 4 / 5 (2+3 are the water's
            // scene color/depth resolve)
            setupCascadeSamplers(shader);
            setupCascadeSamplers(shaderCutout);
            setupCascadeSamplers(shaderLodTerrain);

            // Dissolve band geometry (fixed for a given near radius)
            final float nearFadeEnd = (radius - 0.5f) * EngineConfig.CHUNK_SIZE;
            final float nearFadeStart = nearFadeEnd - RendererConfig.NEAR_FADE_BAND_BLOCKS;
            final float lodNearCut = nearFadeStart - 8f;
            final float shadowTexel = 1.0f / RendererConfig.SHADOW_MAP_SIZE;

            // Render-space Y of the water surface (top of the sea-level block)
            final float waterTopY =
                    EngineConfig.SEA_LEVEL - EngineConfig.MIN_Y + 1f - EngineConfig.WATER_TOP_DELTA;

            Camera camera = new Camera();
            CameraController controller = new CameraController(camera, window, world);
            GreedyChunkMesher mesher = new GreedyChunkMesher();

            // Optional spawn override: -Dvoxel.camera=x,y,z[,yawDeg,pitchDeg]
            String camProp = System.getProperty("voxel.camera");
            if (camProp != null) {
                String[] p = camProp.split(",");
                camera.setPosition(Float.parseFloat(p[0].trim()),
                        Float.parseFloat(p[1].trim()), Float.parseFloat(p[2].trim()));
                if (p.length >= 5) {
                    camera.yaw = (float) Math.toRadians(Double.parseDouble(p[3].trim()));
                    camera.pitch = (float) Math.toRadians(Double.parseDouble(p[4].trim()));
                }
            }

            // Optional periodic capture: -Dvoxel.autoshot.dir=<dir> -Dvoxel.autoshot.period=<sec>
            final String autoshotDir = System.getProperty("voxel.autoshot.dir");
            final double autoshotPeriod =
                    Double.parseDouble(System.getProperty("voxel.autoshot.period", "0"));
            double nextAutoshot = (autoshotDir != null && autoshotPeriod > 0)
                    ? glfwGetTime() + autoshotPeriod : Double.MAX_VALUE;
            int autoshotIndex = 0;
            boolean f2Down = false;

            double lastTime = glfwGetTime();
            double acc = 0.0;
            int frames = 0;
            int jitterIdx = 0;

            while (!glfwWindowShouldClose(window)) {
                double now = glfwGetTime();
                float dt = (float) (now - lastTime);
                lastTime = now;

                glfwPollEvents();
                controller.update(dt);

                if (fbResized[0]) {
                    fbResized[0] = false;
                    post.resize(fbW[0], fbH[0]);
                }

                // -----------------------------
                // Fog + sky
                // -----------------------------
                fogCycle.setRain01(RendererConfig.DEBUG_RAIN);
                fogCycle.setMist01(RendererConfig.DEBUG_MIST);
                fogCycle.update((float) now, camera.position.y);

                // -----------------------------
                // Underwater state (camera below the water surface?)
                // -----------------------------
                float underwater01 = 0f;
                if (camera.position.y < waterTopY) {
                    int h = world.worldSampler().heightAt(
                            (int) Math.floor(camera.position.x),
                            (int) Math.floor(camera.position.z));
                    if (h < EngineConfig.SEA_LEVEL) underwater01 = 1f;
                }
                // Water tint follows the day cycle so night dives go dark
                float uwDay = 0.08f + 0.92f * fogCycle.day01();
                float uwR = (0.016f + fogCycle.skyTopR() * 0.055f) * uwDay;
                float uwG = (0.10f + fogCycle.skyTopG() * 0.16f) * uwDay;
                float uwB = (0.14f + fogCycle.skyTopB() * 0.20f) * uwDay;

                // -----------------------------
                // Camera & matrices (TAA: subpixel-jittered projection)
                // -----------------------------
                view.set(camera.viewMatrix());
                mvpNoJit.set(proj).mul(view);

                if (RendererConfig.TAA_ENABLED) {
                    jitterIdx = (jitterIdx + 1) & 7;
                    float jx = (HALTON2[jitterIdx] - 0.5f) / fbW[0];
                    float jy = (HALTON3[jitterIdx] - 0.5f) / fbH[0];
                    projJit.translation(2f * jx, 2f * jy, 0f).mul(proj);
                    mvp.set(projJit).mul(view);
                } else {
                    projJit.set(proj);
                    mvp.set(mvpNoJit);
                }
                frustum.set(mvp);

                int centerCx = (int) Math.floor(camera.position.x / EngineConfig.CHUNK_SIZE);
                int centerCz = (int) Math.floor(camera.position.z / EngineConfig.CHUNK_SIZE);

                // Chunk meshes live in array-space Y (0..WORLD_HEIGHT), not world Y.
                // Culling with MIN_Y..MAX_Y clipped away mountain tops above world Y ~255.
                float minY = 0f;
                float maxY = EngineConfig.WORLD_HEIGHT;

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
                // REQUEST: far-field LOD tiles (1 tile = 1 region = 256 blocks)
                // -----------------------------
                int centerTx = java.lang.Math.floorDiv(centerCx, EngineConfig.REGION_SIZE_CHUNKS);
                int centerTz = java.lang.Math.floorDiv(centerCz, EngineConfig.REGION_SIZE_CHUNKS);
                float lodMinY = -80f; // skirts reach below array-space 0
                float lodMaxY = EngineConfig.WORLD_HEIGHT;

                lodCache.requestAround(
                    centerTx,
                    centerTz,
                    RendererConfig.LOD_VIEW_TILES,
                    RendererConfig.LOD_SUBMIT_BUDGET_PER_FRAME,
                    (tx, tz, step) -> lodMesher.buildTileMeshes(world.lodProvider().sampleTile(tx, tz, step)),
                    frustum,
                    lodMinY,
                    lodMaxY
                );

                // -----------------------------
                // UPLOAD
                // -----------------------------
                opaqueCache.uploadReady(RendererConfig.UPLOAD_BUDGET_PER_FRAME);
                cutoutCache.uploadReady(RendererConfig.UPLOAD_BUDGET_PER_FRAME);
                translucentCache.uploadReady(RendererConfig.UPLOAD_BUDGET_PER_FRAME);
                lodCache.uploadReady(RendererConfig.LOD_UPLOAD_BUDGET_PER_FRAME);

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
                lodCache.computeVisible(frustum, lodMinY, lodMaxY);

                // -----------------------------
                // SHADOW PASS (sun above horizon only): 3 cascades
                // -----------------------------
                float shadowStrength = 0f;
                if (RendererConfig.SHADOWS_ENABLED) {
                    // NB: JOML's clamp is (min, max, value) — the upper bound is
                    // load-bearing here, the ramp input exceeds 1 for most of the day
                    float sunUp = Math.clamp(0f, 1f, (fogCycle.sunDirY() - 0.06f) / 0.20f);
                    shadowStrength = sunUp * sunUp * (3f - 2f * sunUp) * RendererConfig.SHADOW_STRENGTH;
                }

                if (shadowStrength > 0.001f) {
                    // Light basis: rotation-only view from the sun direction,
                    // ortho box around the camera snapped to shadow texels
                    lightTmp.set(fogCycle.sunDirX(), fogCycle.sunDirY(), fogCycle.sunDirZ()).normalize();
                    float upZ = (Math.abs(lightTmp.y) > 0.95f) ? 1f : 0f;
                    lightView.setLookAt(
                            lightTmp.x * 600f, lightTmp.y * 600f, lightTmp.z * 600f,
                            0f, 0f, 0f,
                            0f, 1f - upZ, upZ);

                    Vector3f cls = lightView.transformPosition(
                            camera.position.x, 130f, camera.position.z, new Vector3f());

                    glEnable(GL_DEPTH_TEST);
                    glDepthMask(true);
                    glDisable(GL_BLEND);
                    atlasTex.bind(0);

                    for (int ci = 0; ci < cascades; ci++) {
                        float ext = RendererConfig.SHADOW_CASCADE_EXTENTS[ci];
                        float zRange = ext + 500f;
                        float texelWorld = (2f * ext) / RendererConfig.SHADOW_MAP_SIZE;
                        float cx = (float) java.lang.Math.floor(cls.x / texelWorld) * texelWorld;
                        float cy = (float) java.lang.Math.floor(cls.y / texelWorld) * texelWorld;

                        lightProj.setOrtho(cx - ext, cx + ext, cy - ext, cy + ext,
                                -cls.z - zRange, -cls.z + zRange);
                        lightMVPs[ci].set(lightProj).mul(lightView);

                        shadowMaps[ci].bindForWriting();

                        // Near-field chunk casters (cascade 0 only needs its
                        // own footprint; the far cascade skips leaf shadows)
                        shaderShadowDepth.use();
                        shaderShadowDepth.setUniformMat4("uLightMVP", lightMVPs[ci]);
                        int chunkRadius = (ci == 0)
                                ? java.lang.Math.min(radius + 1,
                                        (int) java.lang.Math.ceil(ext / EngineConfig.CHUNK_SIZE) + 1)
                                : radius + 1;
                        opaqueCache.drawWithin(centerCx, centerCz, chunkRadius);
                        if (ci < cascades - 1) cutoutCache.drawWithin(centerCx, centerCz, chunkRadius);

                        // Far cascades: LOD terrain casts too, so mountains
                        // shade valleys well past the near field
                        if (ci >= 1) {
                            int lodRadiusTiles =
                                    (int) java.lang.Math.ceil(ext / EngineConfig.REGION_SIZE_BLOCKS) + 1;
                            shaderLodShadow.use();
                            shaderLodShadow.setUniformMat4("uLightMVP", lightMVPs[ci]);
                            lodCache.drawTerrainWithin(centerTx, centerTz, lodRadiusTiles);
                        }
                    }

                    shadowMaps[cascades - 1].unbind();
                    glViewport(0, 0, fbW[0], fbH[0]);
                }

                // -----------------------------
                // MAIN PASS - DRAW into the HDR scene target
                // -----------------------------
                post.beginScene();

                invProj.set(projJit).invert();
                invViewRot.set(view).invert();
                sky.render(fogCycle, invProj, invViewRot, camera.position, (float) now,
                        underwater01, uwR, uwG, uwB);

                atlasTex.bind(0);
                shadowMap0.bindTexture(1);
                shadowMap1.bindTexture(4);
                shadowMap2.bindTexture(5);

                // ---- PASS 1: OPAQUE ----
                glEnable(GL_DEPTH_TEST);
                glDepthMask(true);
                glDisable(GL_BLEND);

                applyPerFrameUniforms(shader, camera, mvp, fogCycle);
                shader.setUniform1f("uNearFadeStart", nearFadeStart);
                shader.setUniform1f("uNearFadeEnd", nearFadeEnd);
                applyShadowUniforms(shader, lightMVPs, shadowStrength, shadowTexel, cascSplits, cascBias);
                applyCloudShadowUniforms(shader, fogCycle, (float) now);
                applyUnderwaterUniforms(shader, underwater01, waterTopY, uwR, uwG, uwB);
                int drawnOpaque = opaqueCache.drawKeys(visibleKeys);

                // ---- PASS 1b: FAR-FIELD LOD TERRAIN ----
                // Drawn after near opaque so identical-depth fragments resolve
                // to the real chunks (LOD is also biased slightly downward).
                applyPerFrameUniforms(shaderLodTerrain, camera, mvp, fogCycle);
                shaderLodTerrain.setUniform1f("uLodNearCut", lodNearCut);
                applyShadowUniforms(shaderLodTerrain, lightMVPs, shadowStrength, shadowTexel, cascSplits, cascBias);
                applyCloudShadowUniforms(shaderLodTerrain, fogCycle, (float) now);
                applyUnderwaterUniforms(shaderLodTerrain, underwater01, waterTopY, uwR, uwG, uwB);
                int drawnLod = lodCache.drawTerrain();

                // ---- PASS 2: CUTOUT ----
                applyPerFrameUniforms(shaderCutout, camera, mvp, fogCycle);
                shaderCutout.setUniform1f("uNearFadeStart", nearFadeStart);
                shaderCutout.setUniform1f("uNearFadeEnd", nearFadeEnd);
                applyShadowUniforms(shaderCutout, lightMVPs, shadowStrength, shadowTexel, cascSplits, cascBias);
                applyCloudShadowUniforms(shaderCutout, fogCycle, (float) now);
                applyUnderwaterUniforms(shaderCutout, underwater01, waterTopY, uwR, uwG, uwB);
                int drawnCutout = cutoutCache.drawKeys(visibleKeys);

                // Opaque world is complete: resolve color+depth so the water
                // pass can refract the scene and march reflections
                post.resolveOpaqueForWater(2, 3);

                // ---- PASS 3: TRANSLUCENT ----
                glDepthMask(false);
                glEnable(GL_BLEND);
                glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
                // Only while diving: water top faces must be visible from
                // below. Above water, culling must stay on — water doesn't
                // write depth, so back faces of shore side-quads would
                // otherwise bleed through the surface.
                if (underwater01 > 0.5f) glDisable(GL_CULL_FACE);

                // Far-field LOD water first (it is always farther than the
                // near-field translucent chunks drawn after it)
                applyPerFrameUniforms(shaderLodWater, camera, mvp, fogCycle);
                shaderLodWater.setUniform1f("uTime", (float) now);
                shaderLodWater.setUniform3f("uSunDir",
                        fogCycle.sunDirX(), fogCycle.sunDirY(), fogCycle.sunDirZ());
                shaderLodWater.setUniform3f("uSkyTopColor",
                        fogCycle.skyTopR(), fogCycle.skyTopG(), fogCycle.skyTopB());
                shaderLodWater.setUniform3f("uSkyHorizonColor",
                        fogCycle.skyHorizonR(), fogCycle.skyHorizonG(), fogCycle.skyHorizonB());
                shaderLodWater.setUniform1f("uLodNearCut", lodNearCut);
                applyUnderwaterUniforms(shaderLodWater, underwater01, waterTopY, uwR, uwG, uwB);
                int drawnLodWater = lodCache.drawWater();

                applyPerFrameUniforms(shaderTranslucent, camera, mvp, fogCycle);
                shaderTranslucent.setUniform1f("uTime", (float) now);
                shaderTranslucent.setUniform1f("uNearFadeStart", nearFadeStart);
                shaderTranslucent.setUniform1f("uNearFadeEnd", nearFadeEnd);
                shaderTranslucent.setUniform1i("uSceneColor", 2);
                shaderTranslucent.setUniform1i("uSceneDepth", 3);
                shaderTranslucent.setUniform2f("uScreenSize", fbW[0], fbH[0]);
                shaderTranslucent.setUniform1f("uNearPlane", RendererConfig.CAMERA_NEAR);
                shaderTranslucent.setUniform1f("uFarPlane", RendererConfig.CAMERA_FAR);
                applyUnderwaterUniforms(shaderTranslucent, underwater01, waterTopY, uwR, uwG, uwB);

                // Sky colors and sun direction for water reflections
                shaderTranslucent.setUniform3f("uSkyTopColor",
                        fogCycle.skyTopR(), fogCycle.skyTopG(), fogCycle.skyTopB());
                shaderTranslucent.setUniform3f("uSkyHorizonColor",
                        fogCycle.skyHorizonR(), fogCycle.skyHorizonG(), fogCycle.skyHorizonB());
                shaderTranslucent.setUniform3f("uSunDir",
                        fogCycle.sunDirX(), fogCycle.sunDirY(), fogCycle.sunDirZ());

                float camChunkX = camera.position.x / EngineConfig.CHUNK_SIZE;
                float camChunkZ = camera.position.z / EngineConfig.CHUNK_SIZE;

                int drawnTrans = translucentCache.drawKeysSorted(
                    visibleKeys,
                    camChunkX,
                    camChunkZ
                );

                if (underwater01 > 0.5f) glEnable(GL_CULL_FACE);
                glDisable(GL_BLEND);
                glDepthMask(true);

                // -----------------------------
                // POST: TAA + bloom + god rays + ACES tonemap -> default framebuffer
                // -----------------------------
                invViewProj.set(mvpNoJit).invert();
                post.resolveAndTaa(invViewProj, prevViewProj, RendererConfig.TAA_ENABLED);
                prevViewProj.set(mvpNoJit);

                float sunU = 0f;
                float sunV = 0f;
                float sunVis = 0f;
                sunClip.set(
                        camera.position.x + fogCycle.sunDirX() * 4000f,
                        camera.position.y + fogCycle.sunDirY() * 4000f,
                        camera.position.z + fogCycle.sunDirZ() * 4000f,
                        1f);
                mvpNoJit.transform(sunClip);
                if (sunClip.w > 0.01f) {
                    sunU = sunClip.x / sunClip.w * 0.5f + 0.5f;
                    sunV = sunClip.y / sunClip.w * 0.5f + 0.5f;
                    float fu = 1f - Math.clamp(0f, 1f, (Math.abs(sunU - 0.5f) - 0.5f) / 0.25f);
                    float fv = 1f - Math.clamp(0f, 1f, (Math.abs(sunV - 0.5f) - 0.5f) / 0.25f);
                    sunVis = fu * fv * Math.clamp(0f, 1f, (fogCycle.sunDirY() + 0.05f) / 0.20f);
                }
                if (underwater01 > 0.5f) sunVis *= RendererConfig.UNDERWATER_GODRAY_MUL;

                post.composite(fbW[0], fbH[0], sunU, sunV, sunVis, 1.0f, 0.92f, 0.78f,
                        (float) now, underwater01);

                // -----------------------------
                // EVICT (throttled - not every frame)
                // -----------------------------
                if (++evictCounter >= RendererConfig.EVICT_INTERVAL_FRAMES) {
                    evictCounter = 0;
                    opaqueCache.evictOutside(centerCx, centerCz, evictRadius);
                    cutoutCache.evictOutside(centerCx, centerCz, evictRadius);
                    translucentCache.evictOutside(centerCx, centerCz, evictRadius);
                    lodCache.evictOutside(centerTx, centerTz, RendererConfig.LOD_VIEW_TILES + 1);

                    // Evict core data (regions + chunks) to prevent memory leaks
                    if (world.streamingControl() != null) {
                        world.streamingControl().evictOutside(centerCx, centerCz, evictRadius + 4);
                    }
                }

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

                    debug.append("LOD:     D=").append(drawnLod)
                            .append(" W=").append(drawnLodWater)
                            .append(" M=").append(lodCache.meshedTileCount())
                            .append('/').append(lodCache.tileCount())
                            .append(" F=").append(lodCache.inFlightCount())
                            .append(" view=").append(RendererConfig.LOD_VIEW_TILES * EngineConfig.REGION_SIZE_BLOCKS)
                            .append("m\n");

                    // Core cache stats
                    if (world.streamingControl() != null) {
                        debug.append("Core:    Regions=").append(world.streamingControl().regionCount())
                                .append(" Chunks=").append(world.streamingControl().chunkCount()).append('\n');
                    }

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

                // F2: screenshot to ./screenshots (like Minecraft)
                boolean f2 = glfwGetKey(window, GLFW_KEY_F2) == GLFW_PRESS;
                if (f2 && !f2Down) {
                    org.aouessar.renderer.gl.Screenshot.save(
                            new java.io.File("screenshots", "shot_" + System.currentTimeMillis() + ".png"),
                            fbW[0], fbH[0]);
                }
                f2Down = f2;

                if (now >= nextAutoshot) {
                    nextAutoshot = now + autoshotPeriod;
                    org.aouessar.renderer.gl.Screenshot.save(
                            new java.io.File(autoshotDir, "auto_" + (autoshotIndex++) + ".png"),
                            fbW[0], fbH[0]);
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
        shader.setUniform2f("uTileSize",
                (RendererConfig.ATLAS_TILE_SIZE / RendererConfig.ATLAS_WIDTH),
                (RendererConfig.ATLAS_TILE_SIZE / RendererConfig.ATLAS_HEIGHT)
        );

        setupFogUniforms(shader);
    }

    /** Fog constants only — for shaders without an atlas (LOD terrain/water). */
    private void setupFogUniforms(GlShaderProgram shader) {
        shader.use();
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

    /** Shadow cascade texture units: 1 / 4 / 5. */
    private void setupCascadeSamplers(GlShaderProgram shader) {
        shader.use();
        shader.setUniform1i("uShadowMap0", 1);
        shader.setUniform1i("uShadowMap1", 4);
        shader.setUniform1i("uShadowMap2", 5);
    }

    private void applyShadowUniforms(
            GlShaderProgram shader, Matrix4f[] lightMVPs,
            float strength, float texel, float[] splits, float[] bias
    ) {
        shader.setUniform1f("uShadowStrength", strength);
        shader.setUniform1f("uShadowTexel", texel);
        shader.setUniform3f("uCascadeSplits", splits[0], splits[1], splits[2]);
        shader.setUniform3f("uCascadeBias", bias[0], bias[1], bias[2]);
        if (strength > 0.001f) {
            shader.setUniformMat4("uLightMVP0", lightMVPs[0]);
            shader.setUniformMat4("uLightMVP1", lightMVPs[1]);
            shader.setUniformMat4("uLightMVP2", lightMVPs[2]);
        }
    }

    private void applyCloudShadowUniforms(GlShaderProgram shader, FogCycle fog, float now) {
        shader.setUniform3f("uSunDir", fog.sunDirX(), fog.sunDirY(), fog.sunDirZ());
        shader.setUniform1f("uTime", now);
        shader.setUniform1f("uCloudCover", RendererConfig.CLOUD_COVER);
        shader.setUniform1f("uCloudHeight", RendererConfig.CLOUD_HEIGHT);
        shader.setUniform1f("uCloudShadowStrength", RendererConfig.CLOUD_SHADOW_STRENGTH);
    }

    private void applyUnderwaterUniforms(
            GlShaderProgram shader, float underwater01, float waterTopY,
            float uwR, float uwG, float uwB
    ) {
        shader.setUniform1f("uUnderwater", underwater01);
        shader.setUniform1f("uWaterY", waterTopY);
        shader.setUniform3f("uUnderwaterColor", uwR, uwG, uwB);
    }

    private void applyPerFrameUniforms(GlShaderProgram shader, Camera camera, Matrix4f mvp, FogCycle fog) {
        shader.use();
        shader.setUniformMat4("uMVP", mvp);

        shader.setUniform3f("uCameraPos", camera.position.x, camera.position.y, camera.position.z);

        shader.setUniform3f("uFogColor", fog.r(), fog.g(), fog.b());
        shader.setUniform1f("uFogStartMul", fog.startMul());
        shader.setUniform1f("uFogRangeMul", fog.rangeMul());

        // Day/night sunlight: multiplies all world shading
        shader.setUniform3f("uSunLight", fog.lightR(), fog.lightG(), fog.lightB());
    }
}
