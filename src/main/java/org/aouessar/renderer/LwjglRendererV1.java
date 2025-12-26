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

        long window = glfwCreateWindow(1280, 720, "Voxel Renderer v1 (Near Field)", 0, 0);
        if (window == 0) throw new IllegalStateException("Failed to create GLFW window");

        glfwMakeContextCurrent(window);
        glfwSwapInterval(0);
        createCapabilities();

        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);
        glFrontFace(GL_CCW);

        glEnable(GL_DEPTH_TEST);
        glClearColor(0f, 0f, 0f, 1f);

        Atlas atlas = new AtlasLoader().loadFromResources("/atlas.json");
        BlockRenderMap brm = new BlockRenderMap();

        try (GlShaderProgram shader = new GlShaderProgram("/shaders/voxel_tiled.vert", "/shaders/voxel_tiled.frag");
             GlTexture2D atlasTex = new GlTexture2D("/atlas.png");
             ChunkMeshCache meshCache = new ChunkMeshCache(Math.max(1, Runtime.getRuntime().availableProcessors() - 1), 128, GlMeshTiled::new)) {

            shader.use();
            shader.setUniform1i("uAtlas", 0);
            // atlas is 16x16 tiles
            shader.setUniform2f("uTileSize", (16f / 320f), (16f / 320f));

            Camera camera = new Camera();
            CameraController controller = new CameraController(camera, window);

            GreedyChunkMesher mesher = new GreedyChunkMesher();

            double lastTime = glfwGetTime();

            int frames = 0;
            double acc = 0.0;

            int cs = EngineConfig.CHUNK_SIZE;

            while (!glfwWindowShouldClose(window)) {
                double now = glfwGetTime();
                float dt = (float) (now - lastTime);
                lastTime = now;

                // FPS
                acc += dt;
                frames++;
                if (acc >= 1.0) {
                    glfwSetWindowTitle(window,
                    "Voxel Renderer v1 (r=" + radius +
                        ", FPS: " + frames +
                        ", entries=" + meshCache.entryCount() +
                        ", meshes=" + meshCache.meshCount() +
                        ", inFlight=" + meshCache.inFlightCount() + ")"
                    );
                    frames = 0;
                    acc = 0.0;
                }

                glfwPollEvents();
                controller.update(dt);

                int[] width = new int[1];
                int[] height = new int[1];
                glfwGetFramebufferSize(window, width, height);
                glViewport(0, 0, width[0], height[0]);

                glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

                // Camera -> chunk center
                int centerCx = (int) Math.floor(camera.position.x / cs);
                int centerCz = (int) Math.floor(camera.position.z / cs);

                final int SUBMIT_BUDGET_PER_FRAME = 64;  // how many chunk meshes we queue per frame
                final int UPLOAD_BUDGET_PER_FRAME = 128;  // how many finished meshes we upload per frame

                // Ensure meshes around camera (sync in Step 4)
                meshCache.requestRadius(centerCx, centerCz, radius, SUBMIT_BUDGET_PER_FRAME,
                        (mx, mz) -> mesher.buildChunkMesh(chunkProvider, atlas, brm, mx, mz)
                );

                // IMPORTANT: GL upload happens on render thread
                meshCache.uploadReady(UPLOAD_BUDGET_PER_FRAME);

                // Evict far meshes
                meshCache.evictOutside(centerCx, centerCz, evictRadius);

                // MVP
                Matrix4f proj = new Matrix4f()
                        .perspective((float) Math.toRadians(75),
                                (float) width[0] / (float) height[0],
                                0.1f, 8000f);

                Matrix4f view = camera.viewMatrix();
                Matrix4f mvp = new Matrix4f(proj).mul(view);

                shader.use();
                shader.setUniformMat4("uMVP", mvp);
                atlasTex.bind(0);

                meshCache.drawAll();

                glfwSwapBuffers(window);
            }
        } finally {
            glfwDestroyWindow(window);
            glfwTerminate();
        }
    }
}