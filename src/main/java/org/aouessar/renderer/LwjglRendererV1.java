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

        // GL 4.6: multi-draw-indirect batching (GlMeshArena) needs 4.3+;
        // any GPU of the last decade provides 4.6.
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 4);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 6);
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
                org.aouessar.renderer.world.TorchHand torchHand =
                        new org.aouessar.renderer.world.TorchHand();
                org.aouessar.renderer.world.PlayerModel playerModel =
                        new org.aouessar.renderer.world.PlayerModel();
                org.aouessar.renderer.world.AmbientEffects ambient =
                        new org.aouessar.renderer.world.AmbientEffects(world);
                org.aouessar.renderer.world.BlockHighlight blockHighlight =
                        new org.aouessar.renderer.world.BlockHighlight();
                org.aouessar.renderer.ui.UiOverlay uiOverlay =
                        new org.aouessar.renderer.ui.UiOverlay(atlas, brm);

                // Shared mesh arena: all near-field chunk meshes live in a few
                // large buffers; each pass flushes as one multi-draw-indirect.
                org.aouessar.renderer.gl.GlMeshArena meshArena =
                        new org.aouessar.renderer.gl.GlMeshArena();

                ChunkMeshCache opaqueCache = new ChunkMeshCache(
                        Math.max(1, Runtime.getRuntime().availableProcessors() - 1),
                        RendererConfig.MAX_IN_FLIGHT_MESHES,
                        meshArena::upload, meshArena::flush
                );
                ChunkMeshCache cutoutCache = new ChunkMeshCache(
                        Math.max(1, Runtime.getRuntime().availableProcessors() - 1),
                        RendererConfig.MAX_IN_FLIGHT_MESHES,
                        meshArena::upload, meshArena::flush
                );
                ChunkMeshCache translucentCache = new ChunkMeshCache(
                        Math.max(1, Runtime.getRuntime().availableProcessors() - 1),
                        RendererConfig.MAX_IN_FLIGHT_MESHES,
                        meshArena::upload, meshArena::flush
                );
                // LOD tiles get their own arena (9-float layout: pos+color+normal)
                org.aouessar.renderer.gl.GlMeshArena lodArena =
                        new org.aouessar.renderer.gl.GlMeshArena(9, new int[]{3, 3, 3});
                LodMeshCache lodCache = new LodMeshCache(
                        RendererConfig.LOD_WORKER_THREADS,
                        RendererConfig.LOD_MAX_IN_FLIGHT,
                        lodArena::upload, lodArena::flush
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
            final boolean[] menuCloseRequest = {false};

            // Block interaction: break/place with mesh invalidation + hotbar
            BlockInteraction interaction = new BlockInteraction(
                    world, camera, window, opaqueCache, cutoutCache, translucentCache);

            // ESC pause menu: mouse-driven sliders/toggles wired to the
            // mutable config knobs (all read per frame -> instant effect)
            org.aouessar.renderer.ui.PauseMenu menu = new org.aouessar.renderer.ui.PauseMenu();
            menu.slider("Day length", () -> RendererConfig.DAY_LENGTH_SECONDS,
                    v -> RendererConfig.DAY_LENGTH_SECONDS = v, 60f, 1200f, 30f, "%.0fs");
            menu.slider("SSAO strength", () -> RendererConfig.SSAO_STRENGTH,
                    v -> RendererConfig.SSAO_STRENGTH = v, 0f, 1.5f, 0.05f, "%.2f");
            menu.slider("Sun shafts", () -> RendererConfig.VOLUMETRIC_STRENGTH,
                    v -> RendererConfig.VOLUMETRIC_STRENGTH = v, 0f, 1.5f, 0.05f, "%.2f");
            menu.slider("Bloom", () -> RendererConfig.BLOOM_STRENGTH,
                    v -> RendererConfig.BLOOM_STRENGTH = v, 0f, 2f, 0.05f, "%.2f");
            menu.slider("Exposure", () -> RendererConfig.POST_EXPOSURE,
                    v -> RendererConfig.POST_EXPOSURE = v, 0.4f, 4f, 0.1f, "%.2f");
            menu.slider("Shadow strength", () -> RendererConfig.SHADOW_STRENGTH,
                    v -> RendererConfig.SHADOW_STRENGTH = v, 0f, 1f, 0.05f, "%.2f");
            menu.slider("Cloud cover", () -> RendererConfig.CLOUD_COVER,
                    v -> RendererConfig.CLOUD_COVER = v, 0f, 0.95f, 0.05f, "%.2f");
            menu.slider("Rain chance", () -> RendererConfig.WEATHER_RAIN_CHANCE,
                    v -> RendererConfig.WEATHER_RAIN_CHANCE = v, 0f, 1f, 0.05f, "%.2f");
            menu.slider("Torch intensity", () -> RendererConfig.TORCH_INTENSITY,
                    v -> RendererConfig.TORCH_INTENSITY = v, 0f, 3f, 0.1f, "%.2f");
            menu.slider("Night brightness", () -> RendererConfig.NIGHT_LIGHT_FLOOR,
                    v -> RendererConfig.NIGHT_LIGHT_FLOOR = v, 0.02f, 0.6f, 0.02f, "%.2f");
            menu.slider("Walk speed", () -> RendererConfig.PLAYER_WALK_SPEED,
                    v -> RendererConfig.PLAYER_WALK_SPEED = v, 1f, 20f, 0.5f, "%.1f");
            menu.slider("Jump velocity", () -> RendererConfig.PLAYER_JUMP_VELOCITY,
                    v -> RendererConfig.PLAYER_JUMP_VELOCITY = v, 4f, 20f, 0.5f, "%.1f");
            menu.slider("Fly speed", () -> controller.moveSpeed,
                    v -> controller.moveSpeed = v, 5f, 200f, 5f, "%.0f");
            menu.slider("Mouse sensitivity", () -> controller.mouseSensitivity,
                    v -> controller.mouseSensitivity = v, 0.0005f, 0.01f, 0.0005f, "%.4f");
            menu.toggle("TAA", () -> RendererConfig.TAA_ENABLED,
                    v -> RendererConfig.TAA_ENABLED = v);
            menu.toggle("SSAO", () -> RendererConfig.SSAO_ENABLED,
                    v -> RendererConfig.SSAO_ENABLED = v);
            menu.action("Resume", () -> menuCloseRequest[0] = true);
            menu.action("Quit", () -> glfwSetWindowShouldClose(window, true));

            // Optional startup view/avatar: -Dvoxel.view=first|back|front, -Dvoxel.character=elf
            switch (System.getProperty("voxel.view", "first").toLowerCase()) {
                case "back" -> camera.viewMode = Camera.VIEW_THIRD_BACK;
                case "front" -> camera.viewMode = Camera.VIEW_THIRD_FRONT;
                default -> camera.viewMode = Camera.VIEW_FIRST;
            }
            if ("elf".equalsIgnoreCase(System.getProperty("voxel.character", "human"))) {
                playerModel.setVariant(org.aouessar.renderer.world.PlayerModel.Variant.ELF);
            }
            final Vector3f eyePos = new Vector3f();
            final Vector3f viewFwd = new Vector3f();
            final WeatherSystem weather = new WeatherSystem(EngineConfig.WORLD_SEED);

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
            boolean escDown = false;

            // Scripted verification hooks
            final boolean editTest = Boolean.getBoolean("voxel.edittest");
            boolean editTestDone = false;
            if (Boolean.getBoolean("voxel.menutest")) {
                menu.open = true;
                glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_NORMAL);
            }

            // Handheld torch: T toggles the light, H hides/shows the model
            boolean torchOn = true;
            boolean torchShown = true;
            boolean tDown = false;
            boolean hDown = false;
            float torchFade = 1f;
            final Vector3f torchPos = new Vector3f();

            double lastTime = glfwGetTime();
            double acc = 0.0;
            int frames = 0;
            int jitterIdx = 0;

            while (!glfwWindowShouldClose(window)) {
                double now = glfwGetTime();
                float dt = (float) (now - lastTime);
                lastTime = now;

                glfwPollEvents();

                // ESC toggles the pause menu (mouse capture follows)
                boolean escKey = glfwGetKey(window, GLFW_KEY_ESCAPE) == GLFW_PRESS;
                if ((escKey && !escDown) || menuCloseRequest[0]) {
                    if (!menuCloseRequest[0]) menu.open = !menu.open;
                    else menu.open = false;
                    menuCloseRequest[0] = false;
                    glfwSetInputMode(window, GLFW_CURSOR,
                            menu.open ? GLFW_CURSOR_NORMAL : GLFW_CURSOR_DISABLED);
                    if (!menu.open) controller.resetMouseLook();
                }
                escDown = escKey;

                // Scripted edit test: carve a notch + raise a pillar ahead
                if (editTest && !editTestDone && now > 6) {
                    editTestDone = true;
                    Vector3f f = camera.forwardDir();
                    int bx0 = (int) java.lang.Math.floor(camera.position.x + f.x * 7);
                    int by0 = (int) java.lang.Math.floor(camera.position.y + f.y * 7);
                    int bz0 = (int) java.lang.Math.floor(camera.position.z + f.z * 7);
                    for (int dx = -2; dx <= 1; dx++)
                        for (int dy = -3; dy <= 0; dy++)
                            for (int dz = -2; dz <= 1; dz++)
                                interaction.editBlock(bx0 + dx, by0 + dy, bz0 + dz,
                                        org.aouessar.core.world.Blocks.AIR);
                    for (int dy = 0; dy <= 2; dy++)
                        interaction.editBlock(bx0 + 4, by0 + dy, bz0 + 4,
                                org.aouessar.core.world.Blocks.STONE);
                }

                controller.inputEnabled = !menu.open;
                if (!menu.open) {
                    controller.update(dt);
                    interaction.update(dt, controller.isPhysicsOn());
                }

                // -----------------------------
                // Handheld torch state
                // -----------------------------
                boolean tKey = glfwGetKey(window, GLFW_KEY_T) == GLFW_PRESS;
                if (tKey && !tDown) torchOn = !torchOn;
                tDown = tKey;
                boolean hKey = glfwGetKey(window, GLFW_KEY_H) == GLFW_PRESS;
                if (hKey && !hDown) torchShown = !torchShown;
                hDown = hKey;

                // Smooth light fade on toggle + subtle flame flicker
                torchFade += ((torchOn ? 1f : 0f) - torchFade) * Math.min(1f, dt * 10f);
                float torchFlicker = 1f
                        + 0.055f * (float) Math.sin(now * 13.0)
                        + 0.030f * (float) Math.sin(now * 29.7);
                float torchLight = RendererConfig.TORCH_INTENSITY * torchFade * torchFlicker;
                // Light source floats slightly ahead of the camera (kinder
                // gradients when hugging a wall)
                camera.forwardDir().mul(0.4f, torchPos).add(camera.position);

                // Render viewpoint: the player's eye, or the third-person
                // orbit camera (controller keeps it out of terrain)
                camera.eyePosition(eyePos);

                // Player avatar animation + variant switch (C)
                if (controller.consumeVariantToggle()) playerModel.toggleVariant();
                playerModel.update(dt, camera.position, camera.yaw, camera.pitch,
                        controller.isInWater(), controller.isPhysicsOn(), (float) now);

                if (fbResized[0]) {
                    fbResized[0] = false;
                    post.resize(fbW[0], fbH[0]);
                }

                // -----------------------------
                // Weather + fog + sky
                // -----------------------------
                boolean coldBiome = world.worldSampler().biomeIdAt(
                        (int) Math.floor(eyePos.x), (int) Math.floor(eyePos.z))
                        == EngineConfig.BIOME_SNOW;
                weather.update(now, dt, coldBiome);

                fogCycle.setRain01(weather.rain01());
                fogCycle.setMist01(RendererConfig.DEBUG_MIST);
                fogCycle.update((float) now, eyePos.y);

                float cloudCover = java.lang.Math.min(0.95f,
                        RendererConfig.CLOUD_COVER + weather.precip01() * 0.45f);

                ambient.update(dt, now, eyePos, weather, fogCycle.day01());

                // -----------------------------
                // Underwater state (camera below the water surface?)
                // -----------------------------
                float underwater01 = 0f;
                if (eyePos.y < waterTopY) {
                    int h = world.worldSampler().heightAt(
                            (int) Math.floor(eyePos.x),
                            (int) Math.floor(eyePos.z));
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
                sky.render(fogCycle, invProj, invViewRot, eyePos, (float) now,
                        underwater01, uwR, uwG, uwB, cloudCover);

                atlasTex.bind(0);
                shadowMap0.bindTexture(1);
                shadowMap1.bindTexture(4);
                shadowMap2.bindTexture(5);

                // ---- PASS 1: OPAQUE ----
                glEnable(GL_DEPTH_TEST);
                glDepthMask(true);
                glDisable(GL_BLEND);

                applyPerFrameUniforms(shader, eyePos, mvp, fogCycle, torchPos, torchLight);
                shader.setUniform1f("uNearFadeStart", nearFadeStart);
                shader.setUniform1f("uNearFadeEnd", nearFadeEnd);
                applyShadowUniforms(shader, lightMVPs, shadowStrength, shadowTexel, cascSplits, cascBias);
                applyCloudShadowUniforms(shader, fogCycle, (float) now, cloudCover);
                applyUnderwaterUniforms(shader, underwater01, waterTopY, uwR, uwG, uwB);
                int drawnOpaque = opaqueCache.drawKeys(visibleKeys);

                // Player avatar (third person only) — drawn with the opaque
                // world so the later resolve lets water reflect/refract it.
                // Lit by day/night sunlight x cave skylight + torch warmth.
                if (camera.isThirdPerson()) {
                    float avatarSky = controller.skyExposure();
                    playerModel.draw(mvp, camera.position,
                            fogCycle.lightR() * avatarSky + RendererConfig.TORCH_R * 0.35f * torchLight,
                            fogCycle.lightG() * avatarSky + RendererConfig.TORCH_G * 0.35f * torchLight,
                            fogCycle.lightB() * avatarSky + RendererConfig.TORCH_B * 0.35f * torchLight);
                }

                // Fish swim with the opaque world: the water surface drawn
                // later refracts and reflects them like any seabed block
                ambient.drawWaterCritters(mvp, eyePos);

                // Wireframe highlight on the aimed block
                if (interaction.hasTarget && !menu.open) {
                    blockHighlight.draw(mvp, interaction.tx, interaction.ty, interaction.tz);
                }

                // ---- PASS 1b: FAR-FIELD LOD TERRAIN ----
                // Drawn after near opaque so identical-depth fragments resolve
                // to the real chunks (LOD is also biased slightly downward).
                applyPerFrameUniforms(shaderLodTerrain, eyePos, mvp, fogCycle, torchPos, torchLight);
                shaderLodTerrain.setUniform1f("uLodNearCut", lodNearCut);
                applyShadowUniforms(shaderLodTerrain, lightMVPs, shadowStrength, shadowTexel, cascSplits, cascBias);
                applyCloudShadowUniforms(shaderLodTerrain, fogCycle, (float) now, cloudCover);
                applyUnderwaterUniforms(shaderLodTerrain, underwater01, waterTopY, uwR, uwG, uwB);
                int drawnLod = lodCache.drawTerrain();

                // ---- PASS 2: CUTOUT ----
                applyPerFrameUniforms(shaderCutout, eyePos, mvp, fogCycle, torchPos, torchLight);
                shaderCutout.setUniform1f("uNearFadeStart", nearFadeStart);
                shaderCutout.setUniform1f("uNearFadeEnd", nearFadeEnd);
                applyShadowUniforms(shaderCutout, lightMVPs, shadowStrength, shadowTexel, cascSplits, cascBias);
                applyCloudShadowUniforms(shaderCutout, fogCycle, (float) now, cloudCover);
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
                applyPerFrameUniforms(shaderLodWater, eyePos, mvp, fogCycle, torchPos, torchLight);
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

                applyPerFrameUniforms(shaderTranslucent, eyePos, mvp, fogCycle, torchPos, torchLight);
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

                float camChunkX = eyePos.x / EngineConfig.CHUNK_SIZE;
                float camChunkZ = eyePos.z / EngineConfig.CHUNK_SIZE;

                int drawnTrans = translucentCache.drawKeysSorted(
                    visibleKeys,
                    camChunkX,
                    camChunkZ
                );

                if (underwater01 > 0.5f) glEnable(GL_CULL_FACE);
                glDisable(GL_BLEND);
                glDepthMask(true);

                // -----------------------------
                // Weather + ambient atmosphere (rain/snow/leaves/birds/bolts)
                // -----------------------------
                viewFwd.set(camera.forwardDir());
                if (camera.viewMode == Camera.VIEW_THIRD_FRONT) viewFwd.negate();
                ambient.drawAtmosphere(mvp, eyePos, viewFwd, weather, now);

                // -----------------------------
                // Held torch viewmodel: drawn over the world into the HDR
                // scene (before post), so the flame blooms and tonemaps
                // -----------------------------
                if (torchShown && !camera.isThirdPerson()) {
                    torchHand.draw(projJit, (float) now,
                            torchFlicker * (0.08f + 0.92f * torchFade));
                }

                // -----------------------------
                // POST: TAA + bloom + god rays + ACES tonemap -> default framebuffer
                // -----------------------------
                invViewProj.set(mvpNoJit).invert();
                post.resolveAndTaa(invViewProj, prevViewProj, RendererConfig.TAA_ENABLED);
                prevViewProj.set(mvpNoJit);

                // Volumetric sun shafts: march the shadow cascades (0-strength
                // when the sun is down, storming hard, or diving)
                float volStrength = RendererConfig.VOLUMETRIC_ENABLED
                        ? RendererConfig.VOLUMETRIC_STRENGTH * shadowStrength
                        * (1f - weather.precip01() * 0.55f)
                        * (underwater01 > 0.5f ? 0.25f : 1f)
                        : 0f;
                post.volumetric(invViewProj,
                        eyePos.x, eyePos.y, eyePos.z,
                        fogCycle.sunDirX(), fogCycle.sunDirY(), fogCycle.sunDirZ(),
                        lightMVPs[0], lightMVPs[1], lightMVPs[2],
                        cascSplits, cascBias,
                        fogCycle.lightR(), fogCycle.lightG() * 0.96f, fogCycle.lightB() * 0.88f,
                        volStrength,
                        shadowMaps[0], shadowMaps[1], shadowMaps[2]);

                float sunU = 0f;
                float sunV = 0f;
                float sunVis = 0f;
                sunClip.set(
                        eyePos.x + fogCycle.sunDirX() * 4000f,
                        eyePos.y + fogCycle.sunDirY() * 4000f,
                        eyePos.z + fogCycle.sunDirZ() * 4000f,
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
                        (float) now, underwater01, weather.flash01(), invProj);

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

                    debug.append("Torch [T]: ").append(torchOn ? "on" : "off")
                            .append("  model [H]: ").append(torchShown ? "shown" : "hidden")
                            .append('\n');

                    debug.append("Physics [G]: ").append(controller.modeLabel()).append('\n');

                    debug.append("Weather: ").append(weather.label())
                            .append("  wind ").append(String.format("%.1f", weather.windStrength()))
                            .append('\n');

                    debug.append("View [F5]: ")
                            .append(switch (camera.viewMode) {
                                case Camera.VIEW_THIRD_BACK -> "third person";
                                case Camera.VIEW_THIRD_FRONT -> "third person (front)";
                                default -> "first person";
                            })
                            .append("  character [C]: ")
                            .append(playerModel.variant() == PlayerModel.Variant.ELF
                                    ? "Sylwen the elf ranger" : "Aldric the wanderer")
                            .append('\n');

                    acc = 0.0;
                    frames = 0;
                }

                // -----------------------------
                // HUD render (every frame)
                // -----------------------------
                hud.render(fbW[0], fbH[0], 8f, 8f, debug.toString());

                // Crosshair + hotbar (+ menu dim and widgets when paused)
                atlasTex.bind(0);
                uiOverlay.render(fbW[0], fbH[0], interaction, menu.open, 0);
                if (menu.open) {
                    menu.renderAndHandle(window, uiOverlay, hud, fbW[0], fbH[0]);
                }

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
    private static float clamp01(float v, float lo, float hi) {
        return v < lo ? lo : java.lang.Math.min(v, hi);
    }

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

    private void applyCloudShadowUniforms(GlShaderProgram shader, FogCycle fog, float now, float cloudCover) {
        shader.setUniform3f("uSunDir", fog.sunDirX(), fog.sunDirY(), fog.sunDirZ());
        shader.setUniform1f("uTime", now);
        shader.setUniform1f("uCloudCover", cloudCover);
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

    private void applyPerFrameUniforms(GlShaderProgram shader, Vector3f eyePos, Matrix4f mvp, FogCycle fog,
                                       Vector3f torchPos, float torchLight) {
        shader.use();
        shader.setUniformMat4("uMVP", mvp);

        // The render viewpoint (third person: the orbit camera, not the player)
        shader.setUniform3f("uCameraPos", eyePos.x, eyePos.y, eyePos.z);

        shader.setUniform3f("uFogColor", fog.r(), fog.g(), fog.b());
        shader.setUniform1f("uFogStartMul", fog.startMul());
        shader.setUniform1f("uFogRangeMul", fog.rangeMul());

        // Day/night sunlight: multiplies all world shading
        shader.setUniform3f("uSunLight", fog.lightR(), fog.lightG(), fog.lightB());

        // Handheld torch (LOD shaders don't declare these: silently skipped)
        shader.setUniform3f("uTorchPos", torchPos.x, torchPos.y, torchPos.z);
        shader.setUniform1f("uTorchLight", torchLight);
        shader.setUniform3f("uTorchColor",
                RendererConfig.TORCH_R, RendererConfig.TORCH_G, RendererConfig.TORCH_B);
        shader.setUniform1f("uTorchRange", RendererConfig.TORCH_RANGE_BLOCKS);
    }
}
