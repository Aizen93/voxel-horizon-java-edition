package org.aouessar.renderer.camera;

import org.aouessar.core.api.WorldAccess;
import org.aouessar.core.world.Blocks;
import org.aouessar.renderer.RendererConfig;
import org.aouessar.renderer.ui.BiomeTeleportDialog;
import org.aouessar.renderer.ui.TeleportDialog;
import org.aouessar.shared.EngineConfig;
import org.joml.Vector3f;

import static org.lwjgl.glfw.GLFW.*;

/**
 * Camera input with two movement modes, toggled with G:
 * <ul>
 *   <li><b>Fly</b> (default): the classic free camera — WASD + Space/Ctrl,
 *       Shift for speed, no collision.</li>
 *   <li><b>Walk</b>: Minecraft-style physics. A 0.6 x 1.8 AABB (eyes at 1.62)
 *       collides with solid blocks axis-by-axis in small substeps (no
 *       tunneling), gravity pulls at 32 blocks/s², Space jumps from the
 *       ground. With feet in water it becomes <b>swimming</b>: buoyant
 *       damped motion, Space swims up, Ctrl dives, idle sinks slowly, and
 *       pushing against a bank while surfacing hops you out.</li>
 * </ul>
 * All physics runs in render-space Y (world Y − MIN_Y), same as the camera.
 */
public final class CameraController {

    private final Camera camera;
    private final long window;
    private final WorldAccess world;

    private boolean firstMouse = true;
    private double lastX, lastY;

    public float moveSpeed = 25f;
    public float mouseSensitivity = 0.0025f;

    private boolean f9WasDown = false;
    private boolean f10WasDown = false;
    private boolean gWasDown = false;
    private boolean f5WasDown = false;
    private boolean cWasDown = false;

    /** C toggles the avatar variant; renderer reads + clears the request. */
    private boolean variantTogglePending = false;

    // Walk-mode state (optionally start in walk mode: -Dvoxel.physics=true)
    private boolean physicsOn = Boolean.parseBoolean(System.getProperty("voxel.physics", "false"));
    private final Vector3f vel = new Vector3f();
    private boolean grounded = false;
    private boolean inWater = false;
    private boolean pushedWall = false;

    private static final float EPS = 0.001f;
    /** Max distance any axis may travel per collision substep (blocks). */
    private static final float MAX_SUBSTEP_MOVE = 0.45f;

    public CameraController(Camera camera, long window, WorldAccess world) {
        this.camera = camera;
        this.window = window;
        this.world = world;

        glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_DISABLED);

        glfwSetCursorPosCallback(window, (w, xpos, ypos) -> {
            if (firstMouse) {
                lastX = xpos;
                lastY = ypos;
                firstMouse = false;
            }
            double dx = xpos - lastX;
            double dy = ypos - lastY;
            lastX = xpos;
            lastY = ypos;

            camera.yaw += (float) dx * mouseSensitivity;
            camera.pitch -= (float) dy * mouseSensitivity;

            float limit = (float) Math.toRadians(89);
            if (camera.pitch > limit) camera.pitch = limit;
            if (camera.pitch < -limit) camera.pitch = -limit;
        });
    }

    /** HUD label: the G toggle has two states; swimming is automatic. */
    public String modeLabel() {
        if (!physicsOn) return "off (free fly, no collision)";
        return inWater ? "on (swimming)" : "on (walking)";
    }

    public boolean isPhysicsOn() {
        return physicsOn;
    }

    public void update(float dt) {
        if (glfwGetKey(window, GLFW_KEY_ESCAPE) == GLFW_PRESS) {
            glfwSetWindowShouldClose(window, true);
        }

        // G: toggle physics (fly <-> walk)
        boolean gDown = glfwGetKey(window, GLFW_KEY_G) == GLFW_PRESS;
        if (gDown && !gWasDown) {
            physicsOn = !physicsOn;
            vel.zero();
            grounded = false;
        }
        gWasDown = gDown;

        // F5: cycle first person -> third (back) -> third (front)
        boolean f5Down = glfwGetKey(window, GLFW_KEY_F5) == GLFW_PRESS;
        if (f5Down && !f5WasDown) camera.viewMode = (camera.viewMode + 1) % 3;
        f5WasDown = f5Down;

        // C: switch the avatar (human <-> elf)
        boolean cDown = glfwGetKey(window, GLFW_KEY_C) == GLFW_PRESS;
        if (cDown && !cWasDown) variantTogglePending = true;
        cWasDown = cDown;

        if (physicsOn) {
            updateWalk(dt);
        } else {
            updateFly(dt);
        }

        updateThirdPersonCamera();
        handleTeleportDialogs();
    }

    /** True once per C press; reading clears the request. */
    public boolean consumeVariantToggle() {
        boolean v = variantTogglePending;
        variantTogglePending = false;
        return v;
    }

    public boolean isInWater() {
        return inWater;
    }

    /**
     * Sky exposure at the player (0.12 deep cave .. 1 open sky) so the avatar
     * darkens underground like the terrain around it. Scans for the first
     * solid block above the head, mirroring the mesher's depth curve.
     */
    public float skyExposure() {
        int bx = (int) Math.floor(camera.position.x);
        int bz = (int) Math.floor(camera.position.z);
        int headY = (int) Math.floor(camera.position.y + 0.3f);

        for (int dy = 1; dy <= 40; dy++) {
            if (isSolid(bx, headY + dy, bz)) {
                if (dy <= 2) return 0.82f;
                if (dy <= 5) return 0.66f;
                if (dy <= 12) return 0.50f;
                if (dy <= 20) return 0.32f;
                if (dy <= 32) return 0.20f;
                return 0.12f;
            }
        }
        return 1f;
    }

    /**
     * Third person: pull the orbit camera in when terrain sits between it and
     * the player — snap in instantly, ease back out.
     */
    private void updateThirdPersonCamera() {
        if (!camera.isThirdPerson()) return;

        float want = RendererConfig.THIRD_PERSON_DISTANCE;
        Vector3f f = camera.forwardDir();
        float sign = (camera.viewMode == Camera.VIEW_THIRD_BACK) ? -1f : 1f;

        float clear = want;
        for (float d = 0.4f; d <= want; d += 0.2f) {
            float px = camera.position.x + sign * f.x * d;
            float py = camera.position.y + sign * f.y * d;
            float pz = camera.position.z + sign * f.z * d;
            if (isSolid((int) Math.floor(px), (int) Math.floor(py), (int) Math.floor(pz))) {
                clear = Math.max(0.5f, d - 0.35f);
                break;
            }
        }

        if (clear < camera.thirdPersonDist) {
            camera.thirdPersonDist = clear; // snap in: never clip into rock
        } else {
            camera.thirdPersonDist += (clear - camera.thirdPersonDist) * 0.15f;
        }
    }

    // -------------------------------------------------------------------------
    // Fly mode (original free camera)
    // -------------------------------------------------------------------------

    private void updateFly(float dt) {
        float spd = moveSpeed * dt;
        if (glfwGetKey(window, GLFW_KEY_LEFT_SHIFT) == GLFW_PRESS) spd *= 2.5f;

        var forward = camera.forwardDir();
        var right = camera.rightDir();

        if (glfwGetKey(window, GLFW_KEY_W) == GLFW_PRESS) camera.position.fma(spd, forward);
        if (glfwGetKey(window, GLFW_KEY_S) == GLFW_PRESS) camera.position.fma(-spd, forward);
        if (glfwGetKey(window, GLFW_KEY_D) == GLFW_PRESS) camera.position.fma(spd, right);
        if (glfwGetKey(window, GLFW_KEY_A) == GLFW_PRESS) camera.position.fma(-spd, right);

        if (glfwGetKey(window, GLFW_KEY_SPACE) == GLFW_PRESS) camera.position.y += spd;
        if (glfwGetKey(window, GLFW_KEY_LEFT_CONTROL) == GLFW_PRESS) camera.position.y -= spd;
    }

    // -------------------------------------------------------------------------
    // Walk mode (gravity + collision + swimming)
    // -------------------------------------------------------------------------

    private void updateWalk(float dt) {
        // Hitches must not launch the player through walls
        dt = Math.min(dt, 0.25f);

        // While the chunk under the player is still streaming in, its
        // placeholder is all-air (real chunks always have bedrock at the
        // bottom): freeze physics so we don't fall through the world.
        if (blockAt((int) Math.floor(camera.position.x), 0,
                (int) Math.floor(camera.position.z)) == Blocks.AIR) {
            return;
        }

        boolean space = glfwGetKey(window, GLFW_KEY_SPACE) == GLFW_PRESS;
        boolean ctrl = glfwGetKey(window, GLFW_KEY_LEFT_CONTROL) == GLFW_PRESS;
        boolean sprint = glfwGetKey(window, GLFW_KEY_LEFT_SHIFT) == GLFW_PRESS;

        float feetY = camera.position.y - RendererConfig.PLAYER_EYE_HEIGHT;

        // In water when knees or waist are submerged
        boolean wasInWater = inWater;
        inWater = isWaterAt(camera.position.x, feetY + 0.2f, camera.position.z)
                || isWaterAt(camera.position.x, feetY + 1.0f, camera.position.z);

        // Belly-flop damping on entry
        if (inWater && !wasInWater && vel.y < -8f) vel.y = -8f;

        // ---- Horizontal wish direction (yaw only, arcade control) ----
        float cy = (float) Math.cos(camera.yaw);
        float sy = (float) Math.sin(camera.yaw);
        float fwd = (glfwGetKey(window, GLFW_KEY_W) == GLFW_PRESS ? 1f : 0f)
                - (glfwGetKey(window, GLFW_KEY_S) == GLFW_PRESS ? 1f : 0f);
        float str = (glfwGetKey(window, GLFW_KEY_D) == GLFW_PRESS ? 1f : 0f)
                - (glfwGetKey(window, GLFW_KEY_A) == GLFW_PRESS ? 1f : 0f);

        // forward_xz = (cos yaw, sin yaw); right_xz = (-sin yaw, cos yaw)
        float wx = cy * fwd - sy * str;
        float wz = sy * fwd + cy * str;
        float wl = (float) Math.sqrt(wx * wx + wz * wz);
        if (wl > 1e-4f) {
            wx /= wl;
            wz /= wl;
        }

        if (inWater) {
            float speed = RendererConfig.SWIM_SPEED * (sprint ? 1.5f : 1f);
            float k = Math.min(1f, dt * RendererConfig.SWIM_DAMPING);
            vel.x += (wx * speed - vel.x) * k;
            vel.z += (wz * speed - vel.z) * k;

            float targetY = -RendererConfig.SWIM_SINK_SPEED;
            if (space) targetY = RendererConfig.SWIM_UP_SPEED;
            else if (ctrl) targetY = -RendererConfig.SWIM_UP_SPEED;
            vel.y += (targetY - vel.y) * k;

            // Hop out onto the bank: surfacing against a wall while swimming
            if (space && pushedWall) vel.y = RendererConfig.PLAYER_JUMP_VELOCITY * 0.85f;
        } else {
            float speed = RendererConfig.PLAYER_WALK_SPEED
                    * (sprint ? RendererConfig.PLAYER_SPRINT_MULT : 1f);
            vel.x = wx * speed;
            vel.z = wz * speed;

            vel.y -= RendererConfig.PLAYER_GRAVITY * dt;
            if (vel.y < -RendererConfig.PLAYER_TERMINAL_VELOCITY) {
                vel.y = -RendererConfig.PLAYER_TERMINAL_VELOCITY;
            }
            if (space && grounded) vel.y = RendererConfig.PLAYER_JUMP_VELOCITY;
        }

        // ---- Integrate with axis-separated collision, substepped ----
        grounded = false;
        pushedWall = false;
        float maxVel = Math.max(Math.abs(vel.y),
                Math.max(Math.abs(vel.x), Math.abs(vel.z)));
        int steps = Math.max(1, (int) Math.ceil(maxVel * dt / MAX_SUBSTEP_MOVE));
        float stepDt = dt / steps;

        for (int i = 0; i < steps; i++) {
            moveAxisX(vel.x * stepDt);
            moveAxisZ(vel.z * stepDt);
            moveAxisY(vel.y * stepDt);
        }
    }

    private void moveAxisX(float d) {
        if (d == 0f) return;
        float nx = camera.position.x + d;
        float feetY = camera.position.y - RendererConfig.PLAYER_EYE_HEIGHT;
        if (boxCollides(nx, feetY, camera.position.z)) {
            float hw = RendererConfig.PLAYER_HALF_WIDTH;
            float clamped = (d > 0)
                    ? (float) Math.floor(nx + hw) - hw - EPS
                    : (float) Math.floor(nx - hw) + 1f + hw + EPS;
            if (!boxCollides(clamped, feetY, camera.position.z)) {
                camera.position.x = clamped;
            }
            vel.x = 0f;
            pushedWall = true;
        } else {
            camera.position.x = nx;
        }
    }

    private void moveAxisZ(float d) {
        if (d == 0f) return;
        float nz = camera.position.z + d;
        float feetY = camera.position.y - RendererConfig.PLAYER_EYE_HEIGHT;
        if (boxCollides(camera.position.x, feetY, nz)) {
            float hw = RendererConfig.PLAYER_HALF_WIDTH;
            float clamped = (d > 0)
                    ? (float) Math.floor(nz + hw) - hw - EPS
                    : (float) Math.floor(nz - hw) + 1f + hw + EPS;
            if (!boxCollides(camera.position.x, feetY, clamped)) {
                camera.position.z = clamped;
            }
            vel.z = 0f;
            pushedWall = true;
        } else {
            camera.position.z = nz;
        }
    }

    private void moveAxisY(float d) {
        if (d == 0f) return;
        float feetY = camera.position.y - RendererConfig.PLAYER_EYE_HEIGHT;
        float nFeet = feetY + d;
        if (boxCollides(camera.position.x, nFeet, camera.position.z)) {
            float clamped;
            if (d > 0) {
                float h = RendererConfig.PLAYER_HEIGHT;
                clamped = (float) Math.floor(nFeet + h) - h - EPS;
            } else {
                clamped = (float) Math.floor(nFeet) + 1f + EPS;
                grounded = true;
            }
            if (!boxCollides(camera.position.x, clamped, camera.position.z)) {
                camera.position.y = clamped + RendererConfig.PLAYER_EYE_HEIGHT;
            }
            vel.y = 0f;
        } else {
            camera.position.y = nFeet + RendererConfig.PLAYER_EYE_HEIGHT;
        }
    }

    // -------------------------------------------------------------------------
    // Block queries (render-space Y)
    // -------------------------------------------------------------------------

    private boolean boxCollides(float ex, float feetY, float ez) {
        float hw = RendererConfig.PLAYER_HALF_WIDTH;
        float h = RendererConfig.PLAYER_HEIGHT;

        int minX = (int) Math.floor(ex - hw);
        int maxX = (int) Math.floor(ex + hw);
        int minY = (int) Math.floor(feetY);
        int maxY = (int) Math.floor(feetY + h - EPS);
        int minZ = (int) Math.floor(ez - hw);
        int maxZ = (int) Math.floor(ez + hw);

        for (int by = minY; by <= maxY; by++) {
            for (int bx = minX; bx <= maxX; bx++) {
                for (int bz = minZ; bz <= maxZ; bz++) {
                    if (isSolid(bx, by, bz)) return true;
                }
            }
        }
        return false;
    }

    private boolean isSolid(int bx, int renderY, int bz) {
        short id = blockAt(bx, renderY, bz);
        if (id == Blocks.AIR || id == Blocks.WATER) return false;
        if (Blocks.isBillboard(id) || Blocks.isVegetation(id)) return false;
        return true;
    }

    private boolean isWaterAt(float x, float renderY, float z) {
        return blockAt((int) Math.floor(x), (int) Math.floor(renderY), (int) Math.floor(z))
                == Blocks.WATER;
    }

    private short blockAt(int bx, int renderY, int bz) {
        int worldY = renderY + EngineConfig.MIN_Y;
        int cx = Math.floorDiv(bx, EngineConfig.CHUNK_SIZE);
        int cz = Math.floorDiv(bz, EngineConfig.CHUNK_SIZE);
        var chunk = world.chunkProvider().getChunk(cx, cz);
        return chunk.getBlock(
                Math.floorMod(bx, EngineConfig.CHUNK_SIZE),
                worldY,
                Math.floorMod(bz, EngineConfig.CHUNK_SIZE));
    }

    // -------------------------------------------------------------------------
    // Teleport dialogs (F9 position / F10 biome)
    // -------------------------------------------------------------------------

    private void handleTeleportDialogs() {
        boolean f9Down = glfwGetKey(window, GLFW_KEY_F9) == GLFW_PRESS;
        if (f9Down && !f9WasDown) {
            // Release mouse capture so you can click the Swing dialog
            glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_NORMAL);
            try {
                TeleportDialog.prompt((int) camera.position.y).ifPresent(t ->
                        camera.setPosition(t.x() + 0.5f, t.y(), t.z() + 0.5f)
                );
            } finally {
                // Restore FPS mouse look
                glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_DISABLED);

                // Prevent a big camera jump on next mouse event
                firstMouse = true;
            }
        }
        f9WasDown = f9Down;
        boolean f10Down = glfwGetKey(window, GLFW_KEY_F10) == GLFW_PRESS;
        if (f10Down && !f10WasDown) {
            glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_NORMAL);
            try {
                int sx = (int) camera.position.x;
                int sz = (int) camera.position.z;

                BiomeTeleportDialog.prompt(sx, sz, world.biomeLocator(), world.worldSampler()).ifPresent(t ->
                        camera.setPosition(t.x() + 0.5f, t.y(), t.z() + 0.5f)
                );
            } finally {
                glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_DISABLED);
                firstMouse = true;
            }
        }
        f10WasDown = f10Down;
    }
}
