package org.aouessar.renderer;

import org.aouessar.core.api.ChunkProvider;
import org.aouessar.renderer.atlas.Atlas;
import org.aouessar.renderer.atlas.AtlasLoader;
import org.aouessar.renderer.camera.Camera;
import org.aouessar.renderer.camera.CameraController;
import org.aouessar.renderer.gl.GlMesh;
import org.aouessar.renderer.gl.GlShaderProgram;
import org.aouessar.renderer.gl.GlTexture2D;
import org.aouessar.renderer.mesh.ChunkMesher;
import org.aouessar.renderer.mesh.MeshData;
import org.aouessar.renderer.world.BlockRenderMap;
import org.aouessar.shared.EngineConfig;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL.*;
import static org.lwjgl.opengl.GL11.*;

public final class LwjglRendererV1 {

    private final ChunkProvider chunkProvider;
    private final int radius; // near field only v1 (tune)

    public LwjglRendererV1(ChunkProvider chunkProvider, int radius) {
        this.chunkProvider = chunkProvider;
        this.radius = radius;
    }

    public void run() {
        // ---- GLFW init
        if (!glfwInit()) throw new IllegalStateException("Failed to init GLFW");

        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);

        long window = glfwCreateWindow(1280, 720, "Voxel Renderer v1 (Near Field)", 0, 0);
        if (window == 0) throw new IllegalStateException("Failed to create GLFW window");

        glfwMakeContextCurrent(window);
        glfwSwapInterval(0); // uncapped (you can set to 1 for vsync)
        createCapabilities();

        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);
        glFrontFace(GL_CCW);

        glEnable(GL_DEPTH_TEST);

        // ---- Load atlas + shader
        Atlas atlas = new AtlasLoader().loadFromResources("/atlas.json");
        BlockRenderMap brm = new BlockRenderMap();

        try (GlShaderProgram shader = new GlShaderProgram("/shaders/voxel.vert", "/shaders/voxel.frag");
             GlTexture2D atlasTex = new GlTexture2D("/atlas.png")) {

            shader.use();
            shader.setUniform1i("uAtlas", 0);

            Camera camera = new Camera();
            CameraController controller = new CameraController(camera, window);

            ChunkMesher mesher = new ChunkMesher();

            // super simple mesh cache (GPU) for v1
            Map<Long, GlMesh> meshCache = new HashMap<>();

            double lastTime = glfwGetTime();

            int frames = 0;
            double acc = 0.0;

            while (!glfwWindowShouldClose(window)) {
                double now = glfwGetTime();
                float dt = (float) (now - lastTime);
                lastTime = now;

                acc += dt;
                frames++;
                if (acc >= 1.0) {
                    glfwSetWindowTitle(window, "Voxel Renderer v1 (Near Field)(FPS: " + frames + ")");
                    frames = 0;
                    acc = 0.0;
                }

                glfwPollEvents();
                controller.update(dt);

                int width[] = new int[1];
                int height[] = new int[1];
                glfwGetFramebufferSize(window, width, height);
                glViewport(0, 0, width[0], height[0]);

                glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

                // Camera -> chunk center
                int cs = EngineConfig.CHUNK_SIZE;
                int centerCx = (int) Math.floor(camera.position.x / cs);
                int centerCz = (int) Math.floor(camera.position.z / cs);

                // Build meshes around camera (sync, simple)
                for (int dz = -radius; dz <= radius; dz++) {
                    for (int dx = -radius; dx <= radius; dx++) {
                        int cx = centerCx + dx;
                        int cz = centerCz + dz;
                        long key = pack(cx, cz);

                        if (!meshCache.containsKey(key)) {
                            MeshData md = mesher.buildChunkMesh(chunkProvider, atlas, brm, cx, cz);
                            if (!md.isEmpty()) {
                                meshCache.put(key, new GlMesh(md));
                            } else {
                                // keep empty out of cache
                            }
                        }
                    }
                }

                // Evict far meshes (simple square)
                meshCache.entrySet().removeIf(e -> {
                    int cx = unpackX(e.getKey());
                    int cz = unpackZ(e.getKey());
                    if (Math.abs(cx - centerCx) > radius + 2 || Math.abs(cz - centerCz) > radius + 2) {
                        e.getValue().close();
                        return true;
                    }
                    return false;
                });

                // Build MVP
                Matrix4f proj = new Matrix4f()
                        .perspective((float) Math.toRadians(75),
                                (float) width[0] / (float) height[0],
                                0.1f, 2000f);

                Matrix4f view = camera.viewMatrix();
                Matrix4f mvp = new Matrix4f(proj).mul(view);

                shader.use();
                shader.setUniformMat4("uMVP", mvp);
                atlasTex.bind(0);

                // Draw all cached meshes
                for (GlMesh m : meshCache.values()) {
                    m.draw();
                }

                glfwSwapBuffers(window);
            }

            // cleanup meshes
            for (GlMesh m : meshCache.values()) m.close();
            meshCache.clear();
        } finally {
            glfwDestroyWindow(window);
            glfwTerminate();
        }
    }

    // Pack (cx,cz) into a single long for map keys
    private static long pack(int cx, int cz) {
        return (((long) cx) << 32) ^ (cz & 0xffffffffL);
    }
    private static int unpackX(long key) { return (int) (key >> 32); }
    private static int unpackZ(long key) { return (int) key; }
}