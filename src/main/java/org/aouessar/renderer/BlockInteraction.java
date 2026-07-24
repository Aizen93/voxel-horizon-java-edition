package org.aouessar.renderer;

import org.aouessar.core.api.WorldAccess;
import org.aouessar.core.world.Blocks;
import org.aouessar.renderer.camera.Camera;
import org.aouessar.renderer.world.ChunkMeshCache;
import org.aouessar.shared.EngineConfig;
import org.joml.Vector3f;

import static org.lwjgl.glfw.GLFW.*;

/**
 * Break/place blocks with the mouse, Minecraft style:
 * <ul>
 *   <li>A DDA voxel raycast from the eye along the view direction (max 6
 *       blocks) finds the targeted block and the face it was entered through.</li>
 *   <li>LMB breaks (with hold-repeat), RMB places the selected hotbar block
 *       against the hit face — never inside the player while physics is on.</li>
 *   <li>Edits go through {@code ChunkProvider.setBlock} (surviving chunk
 *       eviction) and invalidate the meshes of the edited chunk plus any
 *       neighbor whose border shading/faces the edit can touch.</li>
 * </ul>
 * Also owns the 9-slot hotbar selection (keys 1-9 and the scroll wheel).
 */
public final class BlockInteraction {

    private static final float REACH = 6.0f;
    private static final float REPEAT_SECONDS = 0.22f;

    /** Placeable palette shown in the hotbar. */
    public static final short[] PALETTE = {
            Blocks.GRASS, Blocks.DIRT, Blocks.STONE, Blocks.SAND, Blocks.OAK_LOG,
            Blocks.OAK_LEAVES, Blocks.SANDSTONE, Blocks.GRAVEL, Blocks.SNOW
    };

    private final WorldAccess world;
    private final Camera camera;
    private final long window;
    private final ChunkMeshCache[] caches;

    private int selected = 0;
    private double scrollAccum = 0;

    private float breakCooldown = 0;
    private float placeCooldown = 0;

    // Targeted block this frame (render-space block coords) + entry face
    public boolean hasTarget = false;
    public int tx, ty, tz;
    private int fx, fy, fz;

    public BlockInteraction(WorldAccess world, Camera camera, long window, ChunkMeshCache... caches) {
        this.world = world;
        this.camera = camera;
        this.window = window;
        this.caches = caches;

        glfwSetScrollCallback(window, (w, dx, dy) -> scrollAccum += dy);
    }

    public int selectedSlot() {
        return selected;
    }

    public short selectedBlock() {
        return PALETTE[selected];
    }

    /** Programmatic edit through the same path as mouse edits (tests/tools). */
    public void editBlock(int bx, int renderY, int bz, short id) {
        applyEdit(bx, renderY, bz, id);
    }

    public void update(float dt, boolean physicsOn) {
        // Hotbar selection: number keys + scroll wheel
        for (int i = 0; i < 9; i++) {
            if (glfwGetKey(window, GLFW_KEY_1 + i) == GLFW_PRESS) selected = i;
        }
        while (scrollAccum >= 1) { selected = (selected + 8) % 9; scrollAccum -= 1; }
        while (scrollAccum <= -1) { selected = (selected + 1) % 9; scrollAccum += 1; }

        raycast();

        breakCooldown -= dt;
        placeCooldown -= dt;

        boolean lmb = glfwGetMouseButton(window, GLFW_MOUSE_BUTTON_LEFT) == GLFW_PRESS;
        boolean rmb = glfwGetMouseButton(window, GLFW_MOUSE_BUTTON_RIGHT) == GLFW_PRESS;
        if (!lmb) breakCooldown = 0;
        if (!rmb) placeCooldown = 0;

        if (lmb && hasTarget && breakCooldown <= 0) {
            applyEdit(tx, ty, tz, Blocks.AIR);
            breakCooldown = REPEAT_SECONDS;
        } else if (rmb && hasTarget && placeCooldown <= 0) {
            int px = tx + fx, py = ty + fy, pz = tz + fz;
            if (isReplaceable(blockAt(px, py, pz))
                    && !(physicsOn && intersectsPlayer(px, py, pz))) {
                applyEdit(px, py, pz, PALETTE[selected]);
                placeCooldown = REPEAT_SECONDS;
            }
        }
    }

    // -------------------------------------------------------------------------
    // Raycast (Amanatides & Woo DDA, render-space coords)
    // -------------------------------------------------------------------------

    private void raycast() {
        hasTarget = false;
        Vector3f o = camera.position;
        Vector3f d = camera.forwardDir();

        int bx = (int) Math.floor(o.x);
        int by = (int) Math.floor(o.y);
        int bz = (int) Math.floor(o.z);

        int sx = d.x > 0 ? 1 : -1, sy = d.y > 0 ? 1 : -1, sz = d.z > 0 ? 1 : -1;
        float tdx = Math.abs(d.x) < 1e-6f ? 1e30f : 1f / Math.abs(d.x);
        float tdy = Math.abs(d.y) < 1e-6f ? 1e30f : 1f / Math.abs(d.y);
        float tdz = Math.abs(d.z) < 1e-6f ? 1e30f : 1f / Math.abs(d.z);

        float tmx = tdx * (d.x > 0 ? (bx + 1 - o.x) : (o.x - bx));
        float tmy = tdy * (d.y > 0 ? (by + 1 - o.y) : (o.y - by));
        float tmz = tdz * (d.z > 0 ? (bz + 1 - o.z) : (o.z - bz));

        int lastFx = 0, lastFy = 0, lastFz = 0;
        float t = 0;
        while (t <= REACH) {
            if (t > 0) { // don't target the block the eye is inside
                short id = blockAt(bx, by, bz);
                if (id != Blocks.AIR && id != Blocks.WATER) {
                    hasTarget = true;
                    tx = bx; ty = by; tz = bz;
                    fx = lastFx; fy = lastFy; fz = lastFz;
                    return;
                }
            }
            if (tmx < tmy && tmx < tmz) {
                t = tmx; tmx += tdx; bx += sx;
                lastFx = -sx; lastFy = 0; lastFz = 0;
            } else if (tmy < tmz) {
                t = tmy; tmy += tdy; by += sy;
                lastFx = 0; lastFy = -sy; lastFz = 0;
            } else {
                t = tmz; tmz += tdz; bz += sz;
                lastFx = 0; lastFy = 0; lastFz = -sz;
            }
        }
    }

    // -------------------------------------------------------------------------
    // Edit + remesh
    // -------------------------------------------------------------------------

    private void applyEdit(int bx, int renderY, int bz, short id) {
        int worldY = renderY + EngineConfig.MIN_Y;
        if (!world.chunkProvider().setBlock(bx, worldY, bz, id)) return;

        int cx = Math.floorDiv(bx, EngineConfig.CHUNK_SIZE);
        int cz = Math.floorDiv(bz, EngineConfig.CHUNK_SIZE);
        int lx = Math.floorMod(bx, EngineConfig.CHUNK_SIZE);
        int lz = Math.floorMod(bz, EngineConfig.CHUNK_SIZE);

        // Neighbor meshes read our border blocks (faces, AO, skylight ceilings)
        int dxMin = lx == 0 ? -1 : 0, dxMax = lx == EngineConfig.CHUNK_SIZE - 1 ? 1 : 0;
        int dzMin = lz == 0 ? -1 : 0, dzMax = lz == EngineConfig.CHUNK_SIZE - 1 ? 1 : 0;
        for (int dcx = dxMin; dcx <= dxMax; dcx++) {
            for (int dcz = dzMin; dcz <= dzMax; dcz++) {
                for (ChunkMeshCache c : caches) c.invalidate(cx + dcx, cz + dcz);
            }
        }
    }

    private static boolean isReplaceable(short id) {
        return id == Blocks.AIR || id == Blocks.WATER
                || Blocks.isBillboard(id) || Blocks.isVegetation(id);
    }

    private boolean intersectsPlayer(int bx, int by, int bz) {
        float feetY = camera.position.y - RendererConfig.PLAYER_EYE_HEIGHT;
        float hw = RendererConfig.PLAYER_HALF_WIDTH;
        return bx + 1 > camera.position.x - hw && bx < camera.position.x + hw
                && bz + 1 > camera.position.z - hw && bz < camera.position.z + hw
                && by + 1 > feetY && by < feetY + RendererConfig.PLAYER_HEIGHT;
    }

    private short blockAt(int bx, int renderY, int bz) {
        int worldY = renderY + EngineConfig.MIN_Y;
        int cx = Math.floorDiv(bx, EngineConfig.CHUNK_SIZE);
        int cz = Math.floorDiv(bz, EngineConfig.CHUNK_SIZE);
        return world.chunkProvider().getChunk(cx, cz).getBlock(
                Math.floorMod(bx, EngineConfig.CHUNK_SIZE), worldY,
                Math.floorMod(bz, EngineConfig.CHUNK_SIZE));
    }
}
