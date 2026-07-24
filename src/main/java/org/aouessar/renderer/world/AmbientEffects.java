package org.aouessar.renderer.world;

import org.aouessar.core.api.WorldAccess;
import org.aouessar.core.world.Blocks;
import org.aouessar.renderer.RendererConfig;
import org.aouessar.renderer.gl.GlShaderProgram;
import org.aouessar.shared.EngineConfig;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Random;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * Ambient life and weather visuals, all CPU-simulated:
 * <ul>
 *   <li><b>Rain</b> — wind-slanted streaks falling to the terrain/water
 *       surface of their column (never indoors).</li>
 *   <li><b>Snow</b> — slow swaying flakes with the same rules.</li>
 *   <li><b>Leaves</b> — spotted on real leaf blocks near the camera; they
 *       detach and tumble down in pendulum arcs, flipping as they fall.</li>
 *   <li><b>Birds</b> — small 3D birds (body, head, beak, tail, articulated
 *       flapping wings with glide phases) crossing the sky in flocks on fair
 *       days. Species: crow, gull, sparrow.</li>
 *   <li><b>Fish</b> — little 3D swimmers (body, belly, tail that beats,
 *       dorsal + pectoral fins) in five species of varied size, wandering
 *       inside actual water columns; drawn with the opaque pass so the water
 *       surface refracts them properly.</li>
 *   <li><b>Lightning bolt</b> — on a strike event, a jagged HDR-bright
 *       polyline from the cloud deck to the ground.</li>
 * </ul>
 */
public final class AmbientEffects implements AutoCloseable {

    private static final float MIN_Y_OFF = -EngineConfig.MIN_Y; // world -> render y

    private final GlShaderProgram shader;
    private final int vao;
    private final int vbo;
    private float[] verts = new float[96 * 1024];
    private int floatCount;

    private final WorldAccess world;
    private final Random rng = new Random(12345);

    // ---- Precipitation pool (shared by rain & snow) ----
    private final int precipMax = Math.max(RendererConfig.RAIN_PARTICLES, RendererConfig.SNOW_PARTICLES);
    private final float[] prx = new float[precipMax];
    private final float[] pry = new float[precipMax];
    private final float[] prz = new float[precipMax];
    private final float[] prs = new float[precipMax];
    private int precipActive;

    // ---- Leaves ----
    private static final int LEAF_MAX = RendererConfig.AMBIENT_LEAVES_MAX;
    private final float[] lx = new float[LEAF_MAX];
    private final float[] ly = new float[LEAF_MAX];
    private final float[] lz = new float[LEAF_MAX];
    private final float[] lttl = new float[LEAF_MAX];
    private final float[] lseed = new float[LEAF_MAX];
    private final float[] lpdx = new float[LEAF_MAX]; // pendulum swing direction
    private final float[] lpdz = new float[LEAF_MAX];
    private final float[][] lcol = new float[LEAF_MAX][3];
    private double nextLeafScan = 0;

    // ---- Birds ----
    private static final int BIRDS_MAX = 9;
    private int birdCount = 0;
    private final float[] bx = new float[BIRDS_MAX];
    private final float[] by = new float[BIRDS_MAX];
    private final float[] bz = new float[BIRDS_MAX];
    private final float[] bphase = new float[BIRDS_MAX];
    private final float[] bscale = new float[BIRDS_MAX];
    private int birdSpecies = 0;
    private float birdYaw = 0f;
    private double nextBirdRoll = 20; // first flock rolls early
    private float birdTtl = 0f;

    /** {body rgb, wing rgb, beak rgb} per species: crow, gull, sparrow. */
    private static final float[][][] BIRD_SPECIES = {
            {{0.10f, 0.10f, 0.13f}, {0.07f, 0.07f, 0.09f}, {0.25f, 0.22f, 0.18f}},
            {{0.88f, 0.89f, 0.92f}, {0.60f, 0.63f, 0.68f}, {0.85f, 0.55f, 0.15f}},
            {{0.42f, 0.31f, 0.22f}, {0.30f, 0.22f, 0.15f}, {0.78f, 0.62f, 0.25f}},
    };

    // ---- Fish ----
    private static final int FISH = RendererConfig.AMBIENT_FISH;
    private final float[] fx = new float[FISH];
    private final float[] fy = new float[FISH];
    private final float[] fz = new float[FISH];
    private final float[] fyaw = new float[FISH];
    private final float[] fphase = new float[FISH];
    private final float[] fscale = new float[FISH];
    private final int[] fspecies = new int[FISH];
    private final boolean[] falive = new boolean[FISH];

    /** {back rgb, belly rgb, fin rgb} per species. */
    private static final float[][][] FISH_SPECIES = {
            {{0.42f, 0.38f, 0.30f}, {0.76f, 0.73f, 0.62f}, {0.34f, 0.31f, 0.25f}}, // cod
            {{0.58f, 0.24f, 0.19f}, {0.82f, 0.55f, 0.44f}, {0.46f, 0.19f, 0.15f}}, // salmon
            {{0.88f, 0.62f, 0.12f}, {0.96f, 0.86f, 0.55f}, {0.90f, 0.44f, 0.10f}}, // gold tropical
            {{0.20f, 0.40f, 0.72f}, {0.76f, 0.83f, 0.90f}, {0.14f, 0.28f, 0.55f}}, // blue tropical
            {{0.30f, 0.42f, 0.24f}, {0.66f, 0.70f, 0.52f}, {0.25f, 0.34f, 0.20f}}, // perch
    };
    /** Spawn weights: common lake fish, rarer tropicals. */
    private static final int[] FISH_PICK = {0, 0, 0, 4, 4, 1, 1, 2, 3};

    // ---- Lightning bolt ----
    private static final int BOLT_SEGS = 12;
    private final float[] boltX = new float[BOLT_SEGS + 1];
    private final float[] boltY = new float[BOLT_SEGS + 1];
    private final float[] boltZ = new float[BOLT_SEGS + 1];
    private float boltTtl = 0f;

    private final Vector3f scratch = new Vector3f();
    private final Matrix4f model = new Matrix4f();
    private final Matrix4f partM = new Matrix4f();
    private final Vector3f vtx = new Vector3f();

    public AmbientEffects(WorldAccess world) {
        this.world = world;
        this.shader = new GlShaderProgram(
                RendererConfig.AMBIENT_VERT,
                RendererConfig.AMBIENT_FRAG
        );
        this.vao = glGenVertexArrays();
        this.vbo = glGenBuffers();
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, (long) verts.length * Float.BYTES, GL_STREAM_DRAW);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 7 * Float.BYTES, 0L);
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(1, 4, GL_FLOAT, false, 7 * Float.BYTES, 3L * Float.BYTES);
        glBindVertexArray(0);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
    }

    // -------------------------------------------------------------------------
    // Simulation
    // -------------------------------------------------------------------------

    public void update(float dt, double now, Vector3f eye, WeatherSystem weather, float day01) {
        updatePrecipitation(dt, eye, weather);
        updateLeaves(dt, now, eye, weather);
        updateBirds(dt, now, eye, weather, day01);
        updateFish(dt, eye);

        if (weather.consumeStrike()) spawnBolt(eye);
        boltTtl -= dt;
    }

    private void updatePrecipitation(float dt, Vector3f eye, WeatherSystem w) {
        boolean snow = w.isSnow();
        int pool = snow ? RendererConfig.SNOW_PARTICLES : RendererConfig.RAIN_PARTICLES;
        int target = Math.round(pool * w.precip01());
        float radius = RendererConfig.PRECIP_RADIUS;

        while (precipActive < target && precipActive < precipMax) {
            respawnPrecip(precipActive++, eye, radius, true);
        }
        if (precipActive > target) precipActive = target;

        float fall = snow ? 1.8f : 22f;
        float windMul = snow ? 2.5f : 6f;

        for (int i = 0; i < precipActive; i++) {
            float sway = snow ? (float) Math.sin(pry[i] * 0.7f + prs[i] * 20f) * 0.8f : 0f;
            prx[i] += (w.windX() * windMul + sway) * dt;
            prz[i] += (w.windZ() * windMul + sway * 0.6f) * dt;
            pry[i] -= fall * dt;

            if (pry[i] < surfaceRenderY(prx[i], prz[i])
                    || Math.abs(prx[i] - eye.x) > radius + 8f
                    || Math.abs(prz[i] - eye.z) > radius + 8f) {
                respawnPrecip(i, eye, radius, false);
            }
        }
    }

    private void respawnPrecip(int i, Vector3f eye, float radius, boolean scatterY) {
        float a = rng.nextFloat() * (float) (Math.PI * 2);
        float r = (float) Math.sqrt(rng.nextFloat()) * radius;
        prx[i] = eye.x + (float) Math.cos(a) * r;
        prz[i] = eye.z + (float) Math.sin(a) * r;
        pry[i] = eye.y + 8f + rng.nextFloat() * (scatterY ? 20f : 12f);
        prs[i] = rng.nextFloat();
        if (pry[i] < surfaceRenderY(prx[i], prz[i])) pry[i] = -1000f;
    }

    private void updateLeaves(float dt, double now, Vector3f eye, WeatherSystem w) {
        for (int i = 0; i < LEAF_MAX; i++) {
            if (lttl[i] <= 0f) continue;
            lttl[i] -= dt;

            // Pendulum arcs: the leaf rocks along its own swing direction,
            // sinking fastest mid-swing and hanging at the extremes.
            float phase = (float) now * (1.5f + lseed[i] * 0.9f) + lseed[i] * 31f;
            float swing = (float) Math.sin(phase);
            lx[i] += (w.windX() * 1.5f + lpdx[i] * swing * 0.9f) * dt;
            lz[i] += (w.windZ() * 1.5f + lpdz[i] * swing * 0.9f) * dt;
            ly[i] -= (0.30f + 0.35f * Math.abs((float) Math.cos(phase))) * dt;

            if (ly[i] < surfaceRenderY(lx[i], lz[i])) lttl[i] = 0f;
        }

        // Scout leaf blocks near the camera frequently; shed small clusters
        if (now < nextLeafScan) return;
        nextLeafScan = now + 0.12;
        int tries = 2 + (int) (w.windStrength() * 3f);
        for (int t = 0; t < tries; t++) {
            float px = eye.x + (rng.nextFloat() - 0.5f) * 52f;
            float pz = eye.z + (rng.nextFloat() - 0.5f) * 52f;
            int surface = world.worldSampler().heightAt((int) Math.floor(px), (int) Math.floor(pz));
            for (int wy = surface + 2; wy <= surface + 14; wy++) {
                short id = blockAtWorldY((int) Math.floor(px), wy, (int) Math.floor(pz));
                if (!isLeafBlock(id)) continue;

                int cluster = 1 + rng.nextInt(3);
                for (int c = 0; c < cluster; c++) {
                    int slot = freeLeafSlot();
                    if (slot < 0) return;
                    float[] col = leafColor(id);
                    float a = rng.nextFloat() * (float) (Math.PI * 2);
                    lx[slot] = px + (rng.nextFloat() - 0.5f) * 1.6f;
                    ly[slot] = wy + MIN_Y_OFF + rng.nextFloat() * 0.5f;
                    lz[slot] = pz + (rng.nextFloat() - 0.5f) * 1.6f;
                    lttl[slot] = 9f + rng.nextFloat() * 6f;
                    lseed[slot] = rng.nextFloat();
                    lpdx[slot] = (float) Math.cos(a);
                    lpdz[slot] = (float) Math.sin(a);
                    lcol[slot][0] = col[0] * (0.85f + rng.nextFloat() * 0.3f);
                    lcol[slot][1] = col[1] * (0.85f + rng.nextFloat() * 0.3f);
                    lcol[slot][2] = col[2] * (0.85f + rng.nextFloat() * 0.3f);
                }
                break;
            }
        }
    }

    private int freeLeafSlot() {
        for (int i = 0; i < LEAF_MAX; i++) {
            if (lttl[i] <= 0f) return i;
        }
        return -1;
    }

    private void updateBirds(float dt, double now, Vector3f eye, WeatherSystem w, float day01) {
        if (birdCount > 0) {
            birdTtl -= dt;
            float dx = (float) Math.cos(birdYaw) * 9f;
            float dz = (float) Math.sin(birdYaw) * 9f;
            for (int i = 0; i < birdCount; i++) {
                bx[i] += dx * dt;
                bz[i] += dz * dt;
                by[i] += (float) Math.sin(now * 0.7 + bphase[i]) * 0.3f * dt;
                bphase[i] += dt * (8.5f + (i % 3));
            }
            if (birdTtl <= 0f) birdCount = 0;
            return;
        }

        if (now < nextBirdRoll) return;
        nextBirdRoll = now + RendererConfig.BIRD_SPAWN_INTERVAL * (0.6 + rng.nextFloat());
        if (day01 < 0.4f || w.precip01() > 0.4f) return;

        birdCount = 4 + rng.nextInt(BIRDS_MAX - 3);
        birdSpecies = rng.nextInt(BIRD_SPECIES.length);
        birdYaw = rng.nextFloat() * (float) (Math.PI * 2);
        float ox = eye.x - (float) Math.cos(birdYaw) * 70f;
        float oz = eye.z - (float) Math.sin(birdYaw) * 70f;
        float oy = eye.y + 22f + rng.nextFloat() * 18f;
        for (int i = 0; i < birdCount; i++) {
            bx[i] = ox + (rng.nextFloat() - 0.5f) * 14f;
            by[i] = oy + (rng.nextFloat() - 0.5f) * 5f;
            bz[i] = oz + (rng.nextFloat() - 0.5f) * 14f;
            bphase[i] = rng.nextFloat() * 6f;
            bscale[i] = 0.85f + rng.nextFloat() * 0.45f;
        }
        birdTtl = 24f;
    }

    private void updateFish(float dt, Vector3f eye) {
        for (int i = 0; i < FISH; i++) {
            if (!falive[i]) {
                float px = eye.x + (rng.nextFloat() - 0.5f) * 24f;
                float pz = eye.z + (rng.nextFloat() - 0.5f) * 24f;
                int surface = world.worldSampler().heightAt((int) Math.floor(px), (int) Math.floor(pz));
                if (surface >= EngineConfig.SEA_LEVEL) continue;
                float py = (surface + 2 + rng.nextFloat()
                        * Math.max(1, EngineConfig.SEA_LEVEL - surface - 3)) + MIN_Y_OFF;
                if (!isWaterAt(px, py, pz)) continue;
                fx[i] = px;
                fy[i] = py;
                fz[i] = pz;
                fyaw[i] = rng.nextFloat() * (float) (Math.PI * 2);
                fphase[i] = rng.nextFloat() * 6f;
                fspecies[i] = FISH_PICK[rng.nextInt(FISH_PICK.length)];
                float base = 0.6f + rng.nextFloat();
                if (fspecies[i] == 2 || fspecies[i] == 3) base *= 0.7f; // tropicals run small
                fscale[i] = base;
                falive[i] = true;
                continue;
            }

            // Small fish flick faster; speed scales gently with size
            float speed = 0.65f + 0.35f / fscale[i];
            fphase[i] += dt * (5.5f + 3.5f / fscale[i]);
            fyaw[i] += (rng.nextFloat() - 0.5f) * dt * 2.2f;
            float nx = fx[i] + (float) Math.cos(fyaw[i]) * speed * dt;
            float nz = fz[i] + (float) Math.sin(fyaw[i]) * speed * dt;
            float ny = fy[i] + (float) Math.sin(fphase[i] * 0.19f) * 0.15f * dt;

            if (isWaterAt(nx, ny, nz)) {
                fx[i] = nx;
                fy[i] = ny;
                fz[i] = nz;
            } else {
                fyaw[i] += (float) Math.PI * (0.6f + rng.nextFloat() * 0.4f);
            }

            float ddx = fx[i] - eye.x, ddz = fz[i] - eye.z;
            if (ddx * ddx + ddz * ddz > 30f * 30f) falive[i] = false;
        }
    }

    private void spawnBolt(Vector3f eye) {
        float a = rng.nextFloat() * (float) (Math.PI * 2);
        float dist = 45f + rng.nextFloat() * 90f;
        float gx = eye.x + (float) Math.cos(a) * dist;
        float gz = eye.z + (float) Math.sin(a) * dist;
        float groundY = surfaceRenderY(gx, gz);
        float topY = RendererConfig.CLOUD_HEIGHT;

        for (int s = 0; s <= BOLT_SEGS; s++) {
            float t = s / (float) BOLT_SEGS;
            boltY[s] = topY + (groundY - topY) * t;
            float jag = (s == 0 || s == BOLT_SEGS) ? 0f : 1f;
            boltX[s] = gx + (rng.nextFloat() - 0.5f) * 14f * jag * (1f - t * 0.5f);
            boltZ[s] = gz + (rng.nextFloat() - 0.5f) * 14f * jag * (1f - t * 0.5f);
        }
        boltTtl = 0.28f;
    }

    // -------------------------------------------------------------------------
    // Drawing: fish (opaque pass, so water refracts them)
    // -------------------------------------------------------------------------

    public void drawWaterCritters(Matrix4f mvp, Vector3f eye) {
        floatCount = 0;

        for (int i = 0; i < FISH; i++) {
            if (!falive[i]) continue;
            float[][] pal = FISH_SPECIES[fspecies[i]];
            float s = fscale[i];
            float tailBeat = (float) Math.sin(fphase[i]) * 0.5f;

            model.identity()
                    .translate(fx[i], fy[i], fz[i])
                    .rotateY(-fyaw[i])
                    .scale(s);

            // Body + belly + snout
            box(model, 0f, 0f, 0f, 0.17f, 0.062f, 0.045f, pal[0]);
            box(model, 0.01f, -0.045f, 0f, 0.13f, 0.028f, 0.042f, pal[1]);
            box(model, 0.20f, -0.006f, 0f, 0.05f, 0.045f, 0.032f, pal[0]);

            // Tail: hinged at the body's rear, beating side to side
            partM.set(model)
                    .translate(-0.17f, 0f, 0f)
                    .rotateY(tailBeat)
                    .translate(0.17f, 0f, 0f);
            box(partM, -0.245f, 0f, 0f, 0.075f, 0.052f, 0.011f, pal[2]);

            // Dorsal fin + pectorals
            box(model, -0.02f, 0.078f, 0f, 0.06f, 0.026f, 0.008f, pal[2]);
            box(model, 0.06f, -0.035f, 0.052f, 0.032f, 0.008f, 0.026f, pal[2]);
            box(model, 0.06f, -0.035f, -0.052f, 0.032f, 0.008f, 0.026f, pal[2]);
        }

        if (floatCount == 0) return;
        flush(mvp);
    }

    // -------------------------------------------------------------------------
    // Drawing: atmosphere (blended, after the water pass)
    // -------------------------------------------------------------------------

    public void drawAtmosphere(Matrix4f mvp, Vector3f eye, Vector3f camFwd,
                               WeatherSystem w, double now) {
        floatCount = 0;

        scratch.set(camFwd).cross(0f, 1f, 0f).normalize();
        float rx = scratch.x, ry = scratch.y, rz = scratch.z;
        float ux = ry * camFwd.z - rz * camFwd.y;
        float uy = rz * camFwd.x - rx * camFwd.z;
        float uz = rx * camFwd.y - ry * camFwd.x;

        // ---- Precipitation ----
        if (precipActive > 0 && w.isSnow()) {
            float[] c = {0.95f, 0.96f, 1.0f, 0.85f};
            for (int i = 0; i < precipActive; i++) {
                if (pry[i] < -900f) continue;
                float s = 0.045f + prs[i] * 0.03f;
                billboard(prx[i], pry[i], prz[i], rx, ry, rz, ux, uy, uz, s, s, c);
            }
        } else if (precipActive > 0) {
            float[] c = {0.62f, 0.68f, 0.78f, 0.35f};
            float vx = w.windX() * 6f, vy = -22f, vz = w.windZ() * 6f;
            float vl = (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
            float ax = vx / vl * 0.45f, ay = vy / vl * 0.45f, az = vz / vl * 0.45f;
            for (int i = 0; i < precipActive; i++) {
                if (pry[i] < -900f) continue;
                streak(prx[i], pry[i], prz[i], ax, ay, az, rx, ry, rz, 0.014f, c);
            }
        }

        // ---- Leaves: tumbling, foreshortened as they flip ----
        float[] lc = new float[4];
        lc[3] = 0.96f;
        for (int i = 0; i < LEAF_MAX; i++) {
            if (lttl[i] <= 0f) continue;
            lc[0] = lcol[i][0];
            lc[1] = lcol[i][1];
            lc[2] = lcol[i][2];
            float phase = (float) now * (1.5f + lseed[i] * 0.9f) + lseed[i] * 31f;
            float spin = phase * 0.9f + lseed[i] * 9f;
            float ca = (float) Math.cos(spin), sa = (float) Math.sin(spin);
            float r2x = rx * ca + ux * sa, r2y = ry * ca + uy * sa, r2z = rz * ca + uz * sa;
            float u2x = ux * ca - rx * sa, u2y = uy * ca - ry * sa, u2z = uz * ca - rz * sa;
            // Flip foreshortening: the leaf narrows as it turns edge-on
            float flip = Math.max(0.22f, Math.abs((float) Math.cos(phase * 1.7f + lseed[i] * 5f)));
            float len = 0.085f * (0.8f + lseed[i] * 0.5f);
            billboard(lx[i], ly[i], lz[i],
                    r2x, r2y, r2z, u2x, u2y, u2z, len, len * 0.65f * flip, lc);
        }

        // ---- Birds: little 3D fliers with articulated wings ----
        if (birdCount > 0) {
            float[][] pal = BIRD_SPECIES[birdSpecies];
            for (int i = 0; i < birdCount; i++) {
                // Flap bursts alternate with stretches of gliding
                boolean glide = Math.sin(now * 0.45 + i * 1.3) > 0.35;
                float flap = glide ? 0.16f : (float) Math.sin(bphase[i]) * 0.75f;

                model.identity()
                        .translate(bx[i], by[i], bz[i])
                        .rotateY(-birdYaw)
                        .scale(bscale[i]);

                // Body, head, beak, tail
                box(model, 0f, 0f, 0f, 0.14f, 0.052f, 0.05f, pal[0]);
                box(model, 0.155f, 0.045f, 0f, 0.05f, 0.042f, 0.04f, pal[0]);
                box(model, 0.215f, 0.038f, 0f, 0.026f, 0.013f, 0.013f, pal[2]);
                box(model, -0.165f, 0.012f, 0f, 0.06f, 0.011f, 0.036f, pal[1]);

                // Wings: hinged at the shoulders, rolling around the body axis
                for (int side = -1; side <= 1; side += 2) {
                    partM.set(model)
                            .translate(0.02f, 0.03f, side * 0.045f)
                            .rotateX(-side * flap)
                            .translate(-0.02f, -0.03f, -side * 0.045f);
                    box(partM, 0.0f, 0.03f, side * (0.045f + 0.15f),
                            0.085f, 0.009f, 0.15f, pal[1]);
                }
            }
        }

        // ---- Lightning bolt ----
        if (boltTtl > 0f) {
            float flick = 0.6f + 0.4f * (float) Math.sin(now * 90.0);
            float[] core = {6f * flick, 6f * flick, 7.5f * flick, 1f};
            for (int s = 0; s < BOLT_SEGS; s++) {
                streakSeg(boltX[s], boltY[s], boltZ[s],
                        boltX[s + 1], boltY[s + 1], boltZ[s + 1],
                        rx, ry, rz, 0.22f, core);
            }
        }

        if (floatCount == 0) return;

        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glDepthMask(false);
        flush(mvp);
        glDepthMask(true);
        glDisable(GL_BLEND);
    }

    private void flush(Matrix4f mvp) {
        shader.use();
        shader.setUniformMat4("uMVP", mvp);

        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, (long) verts.length * Float.BYTES, GL_STREAM_DRAW);
        glBufferSubData(GL_ARRAY_BUFFER, 0L, java.util.Arrays.copyOf(verts, floatCount));

        boolean cull = glIsEnabled(GL_CULL_FACE);
        if (cull) glDisable(GL_CULL_FACE);
        glDrawArrays(GL_TRIANGLES, 0, floatCount / 7);
        if (cull) glEnable(GL_CULL_FACE);

        glBindVertexArray(0);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
    }

    @Override
    public void close() {
        glDeleteBuffers(vbo);
        glDeleteVertexArrays(vao);
        shader.close();
    }

    // -------------------------------------------------------------------------
    // Geometry helpers
    // -------------------------------------------------------------------------

    /** Transformed box with baked top/bottom face shading (alpha 1). */
    private void box(Matrix4f xf,
                     float cx, float cy, float cz,
                     float hx, float hy, float hz,
                     float[] col) {
        final float[] faceShade = {0.85f, 0.72f, 1.00f, 0.55f, 0.78f, 0.78f};

        float x0 = cx - hx, x1 = cx + hx;
        float y0 = cy - hy, y1 = cy + hy;
        float z0 = cz - hz, z1 = cz + hz;

        float[][][] faces = {
                {{x1, y0, z0}, {x1, y1, z0}, {x1, y1, z1}, {x1, y0, z1}},
                {{x0, y0, z1}, {x0, y1, z1}, {x0, y1, z0}, {x0, y0, z0}},
                {{x0, y1, z0}, {x0, y1, z1}, {x1, y1, z1}, {x1, y1, z0}},
                {{x0, y0, z1}, {x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}},
                {{x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}, {x0, y1, z1}},
                {{x1, y0, z0}, {x0, y0, z0}, {x0, y1, z0}, {x1, y1, z0}},
        };

        ensure(6 * 6 * 7);
        int[][] tris = {{0, 1, 2}, {2, 3, 0}};
        for (int f = 0; f < 6; f++) {
            float sh = faceShade[f];
            float fr = col[0] * sh, fg = col[1] * sh, fb = col[2] * sh;
            for (int[] tri : tris) {
                for (int idx : tri) {
                    vtx.set(faces[f][idx][0], faces[f][idx][1], faces[f][idx][2]);
                    xf.transformPosition(vtx);
                    verts[floatCount++] = vtx.x;
                    verts[floatCount++] = vtx.y;
                    verts[floatCount++] = vtx.z;
                    verts[floatCount++] = fr;
                    verts[floatCount++] = fg;
                    verts[floatCount++] = fb;
                    verts[floatCount++] = 1f;
                }
            }
        }
    }

    /** Camera-facing rectangle (half-width w, half-height h). */
    private void billboard(float x, float y, float z,
                           float rx, float ry, float rz,
                           float ux, float uy, float uz,
                           float w, float h, float[] c) {
        quad(x - rx * w - ux * h, y - ry * w - uy * h, z - rz * w - uz * h,
                x + rx * w - ux * h, y + ry * w - uy * h, z + rz * w - uz * h,
                x + rx * w + ux * h, y + ry * w + uy * h, z + rz * w + uz * h,
                x - rx * w + ux * h, y - ry * w + uy * h, z - rz * w + uz * h, c);
    }

    private void streak(float x, float y, float z,
                        float ax, float ay, float az,
                        float rx, float ry, float rz,
                        float w, float[] c) {
        quad(x - ax - rx * w, y - ay - ry * w, z - az - rz * w,
                x - ax + rx * w, y - ay + ry * w, z - az + rz * w,
                x + ax + rx * w, y + ay + ry * w, z + az + rz * w,
                x + ax - rx * w, y + ay - ry * w, z + az - rz * w, c);
    }

    private void streakSeg(float x0, float y0, float z0,
                           float x1, float y1, float z1,
                           float rx, float ry, float rz,
                           float w, float[] c) {
        quad(x0 - rx * w, y0 - ry * w, z0 - rz * w,
                x0 + rx * w, y0 + ry * w, z0 + rz * w,
                x1 + rx * w, y1 + ry * w, z1 + rz * w,
                x1 - rx * w, y1 - ry * w, z1 - rz * w, c);
    }

    private void quad(float x0, float y0, float z0,
                      float x1, float y1, float z1,
                      float x2, float y2, float z2,
                      float x3, float y3, float z3,
                      float[] c) {
        ensure(6 * 7);
        vert(x0, y0, z0, c);
        vert(x1, y1, z1, c);
        vert(x2, y2, z2, c);
        vert(x2, y2, z2, c);
        vert(x3, y3, z3, c);
        vert(x0, y0, z0, c);
    }

    private void vert(float x, float y, float z, float[] c) {
        verts[floatCount++] = x;
        verts[floatCount++] = y;
        verts[floatCount++] = z;
        verts[floatCount++] = c[0];
        verts[floatCount++] = c[1];
        verts[floatCount++] = c[2];
        verts[floatCount++] = c[3];
    }

    private void ensure(int add) {
        if (floatCount + add > verts.length) {
            verts = java.util.Arrays.copyOf(verts, Math.max(verts.length * 2, floatCount + add));
        }
    }

    // -------------------------------------------------------------------------
    // World queries
    // -------------------------------------------------------------------------

    private float surfaceRenderY(float x, float z) {
        int surface = world.worldSampler().heightAt((int) Math.floor(x), (int) Math.floor(z));
        int top = Math.max(surface, EngineConfig.SEA_LEVEL);
        return top + MIN_Y_OFF + 1f;
    }

    private short blockAtWorldY(int bx, int worldY, int bz) {
        int cx = Math.floorDiv(bx, EngineConfig.CHUNK_SIZE);
        int cz = Math.floorDiv(bz, EngineConfig.CHUNK_SIZE);
        return world.chunkProvider().getChunk(cx, cz).getBlock(
                Math.floorMod(bx, EngineConfig.CHUNK_SIZE), worldY,
                Math.floorMod(bz, EngineConfig.CHUNK_SIZE));
    }

    private boolean isWaterAt(float x, float renderY, float z) {
        return blockAtWorldY((int) Math.floor(x),
                (int) Math.floor(renderY) + EngineConfig.MIN_Y,
                (int) Math.floor(z)) == Blocks.WATER;
    }

    private static boolean isLeafBlock(short id) {
        return id == Blocks.LEAVES || id == Blocks.OAK_LEAVES || id == Blocks.ACACIA_LEAVES
                || id == Blocks.JUNGLE_LEAVES || id == Blocks.SPRUCE_LEAVES || id == Blocks.SNOW_LEAVES;
    }

    private static float[] leafColor(short id) {
        if (id == Blocks.ACACIA_LEAVES) return new float[]{0.55f, 0.55f, 0.25f};
        if (id == Blocks.SNOW_LEAVES) return new float[]{0.80f, 0.85f, 0.82f};
        if (id == Blocks.SPRUCE_LEAVES) return new float[]{0.25f, 0.40f, 0.26f};
        return new float[]{0.38f, 0.52f, 0.22f};
    }
}
