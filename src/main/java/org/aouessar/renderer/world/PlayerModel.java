package org.aouessar.renderer.world;

import org.aouessar.renderer.RendererConfig;
import org.aouessar.renderer.gl.GlShaderProgram;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * The player avatar for third-person view: a Minecraft-proportioned voxel
 * character (1.8 blocks tall) built from colored boxes, rebuilt on the CPU
 * every frame so limbs animate.
 * <p>
 * Two hand-designed variants (C toggles in-game):
 * <ul>
 *   <li><b>HUMAN — the Wanderer</b>: tanned skin, dark brown hair, leather
 *       tunic with a gold belt buckle, slate trousers, worn boots and a
 *       travel backpack.</li>
 *   <li><b>ELF — the Ranger</b>: pale skin, long silver hair, pointed ears,
 *       emerald eyes, forest-green tunic with a gold-trimmed belt and a deep
 *       green cape falling to the knees.</li>
 * </ul>
 * Animation: legs and arms swing counter-phased with movement speed (short
 * fast strokes while swimming), the body turns smoothly toward the movement
 * direction, and the head tracks the camera within human limits. In free-fly
 * mode the limbs relax to a floating pose.
 */
public final class PlayerModel implements AutoCloseable {

    public enum Variant { HUMAN, ELF }

    /** One Minecraft skin pixel: the classic 32px body = 1.8 blocks. */
    private static final float P = 1.8f / 32f;
    private static final float EYE_HEIGHT = RendererConfig.PLAYER_EYE_HEIGHT;

    private final GlShaderProgram shader;
    private final int vao;
    private final int vbo;

    private float[] verts = new float[40 * 36 * 6];
    private int floatCount = 0;

    private Variant variant = Variant.HUMAN;

    // Animation state
    private float bodyYaw = 0f;
    private float phase = 0f;
    private float animAmp = 0f;
    private final Vector3f prevPos = new Vector3f(Float.NaN, 0, 0);
    private float speedXZ = 0f;
    private boolean swimming = false;
    private boolean floating = false;
    private float time = 0f;

    // Scratch matrices (no per-frame allocation)
    private final Matrix4f base = new Matrix4f();
    private final Matrix4f part = new Matrix4f();
    private final Vector3f vtx = new Vector3f();

    public PlayerModel() {
        this.shader = new GlShaderProgram(
                RendererConfig.AVATAR_VERT,
                RendererConfig.AVATAR_FRAG
        );
        this.vao = glGenVertexArrays();
        this.vbo = glGenBuffers();
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, (long) verts.length * Float.BYTES, GL_STREAM_DRAW);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 6 * Float.BYTES, 0L);
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(1, 3, GL_FLOAT, false, 6 * Float.BYTES, 3L * Float.BYTES);
        glBindVertexArray(0);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
    }

    public Variant variant() {
        return variant;
    }

    public void setVariant(Variant v) {
        this.variant = v;
    }

    public void toggleVariant() {
        variant = (variant == Variant.HUMAN) ? Variant.ELF : Variant.HUMAN;
    }

    /**
     * Advance animation. Position is the PLAYER EYE (render space); movement
     * speed and facing are derived from the position delta, so it works in
     * both physics and fly mode.
     */
    public void update(float dt, Vector3f playerEye, float camYaw, float camPitch,
                       boolean inWater, boolean physicsOn, float now) {
        this.time = now;
        this.swimming = inWater && physicsOn;
        this.floating = !physicsOn;

        if (Float.isNaN(prevPos.x)) prevPos.set(playerEye);
        float dx = playerEye.x - prevPos.x;
        float dz = playerEye.z - prevPos.z;
        prevPos.set(playerEye);

        float dist = (float) Math.sqrt(dx * dx + dz * dz);
        float instSpeed = (dt > 1e-5f) ? dist / dt : 0f;
        speedXZ += (instSpeed - speedXZ) * Math.min(1f, dt * 12f);

        // Body turns toward the movement direction; stays put when idle
        if (instSpeed > 0.6f) {
            float target = (float) Math.atan2(dz, dx);
            bodyYaw += wrapAngle(target - bodyYaw) * Math.min(1f, dt * 10f);
        } else if (speedXZ < 0.2f) {
            // Idle: slowly settle toward where the camera looks
            bodyYaw += wrapAngle(camYaw - bodyYaw) * Math.min(1f, dt * 1.5f);
        }

        // Stride: swimming uses shorter, quicker strokes
        float stride = swimming ? 2.2f : 1.55f;
        phase += speedXZ * dt * stride;

        float targetAmp = floating ? 0f
                : Math.min(1f, speedXZ / RendererConfig.PLAYER_WALK_SPEED)
                * (swimming ? 0.35f : 0.55f);
        animAmp += (targetAmp - animAmp) * Math.min(1f, dt * 8f);

        this.camYawIn = camYaw;
        this.camPitchIn = camPitch;
    }

    private float camYawIn = 0f;
    private float camPitchIn = 0f;

    /** Rebuild + draw at the player position. Light = sun/sky x cave + torch. */
    public void draw(Matrix4f mvp, Vector3f playerEye, float lr, float lg, float lb) {
        buildMesh(playerEye);

        shader.use();
        shader.setUniformMat4("uMVP", mvp);
        shader.setUniform3f("uLight", lr, lg, lb);

        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, (long) verts.length * Float.BYTES, GL_STREAM_DRAW);
        glBufferSubData(GL_ARRAY_BUFFER, 0L, java.util.Arrays.copyOf(verts, floatCount));

        boolean cull = glIsEnabled(GL_CULL_FACE);
        if (cull) glDisable(GL_CULL_FACE);
        glDrawArrays(GL_TRIANGLES, 0, floatCount / 6);
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
    // Mesh assembly (model space: origin at the feet, facing +X)
    // -------------------------------------------------------------------------

    private void buildMesh(Vector3f playerEye) {
        floatCount = 0;

        float feetY = playerEye.y - EYE_HEIGHT;
        base.identity()
                .translate(playerEye.x, feetY, playerEye.z)
                .rotateY(-bodyYaw);

        boolean elf = (variant == Variant.ELF);

        // ---- Palette ----
        float[] skin = elf ? rgb(0.92f, 0.80f, 0.70f) : rgb(0.85f, 0.66f, 0.50f);
        float[] hair = elf ? rgb(0.84f, 0.82f, 0.66f) : rgb(0.27f, 0.18f, 0.11f);
        float[] tunic = elf ? rgb(0.16f, 0.42f, 0.23f) : rgb(0.55f, 0.38f, 0.21f);
        float[] belt = elf ? rgb(0.32f, 0.26f, 0.10f) : rgb(0.28f, 0.19f, 0.11f);
        float[] gold = rgb(0.88f, 0.72f, 0.28f);
        float[] pants = elf ? rgb(0.19f, 0.26f, 0.21f) : rgb(0.26f, 0.29f, 0.34f);
        float[] boots = elf ? rgb(0.36f, 0.28f, 0.18f) : rgb(0.21f, 0.15f, 0.10f);
        float[] eyes = elf ? rgb(0.15f, 0.65f, 0.38f) : rgb(0.20f, 0.13f, 0.08f);
        float[] extra = elf ? rgb(0.10f, 0.28f, 0.16f) : rgb(0.42f, 0.28f, 0.16f); // cape / backpack

        float swing = (float) Math.sin(phase) * animAmp;
        float idleSway = (float) Math.sin(time * 1.6f) * 0.03f;
        float relax = floating ? 0.12f : 0f; // floaty arms in fly mode

        // ---- Legs (hip pivot y = 12px) ----
        for (int s = -1; s <= 1; s += 2) {
            float a = swing * s;
            part.set(base).translate(0, 12 * P, 0).rotateZ(a).translate(0, -12 * P, 0);
            addBox(part, 0, 7.5f * P, s * 2 * P, 2 * P, 4.5f * P, 2 * P, pants);
            addBox(part, 0, 1.5f * P, s * 2 * P, 2.1f * P, 1.5f * P, 2.1f * P, boots);
        }

        // ---- Torso + belt ----
        addBox(base, 0, 18 * P, 0, 2 * P, 6 * P, 4 * P, tunic);
        addBox(base, 0, 12.6f * P, 0, 2.2f * P, 1.0f * P, 4.2f * P, belt);
        addBox(base, 2.25f * P, 12.6f * P, 0, 0.3f * P, 0.8f * P, 0.9f * P, gold);
        if (elf) {
            // Gold trim across the chest
            addBox(base, 2.1f * P, 20.5f * P, 0, 0.15f * P, 0.5f * P, 4.05f * P, gold);
        }

        // ---- Cape (elf) / backpack (human) ----
        if (elf) {
            float capeSway = idleSway + speedXZ * 0.02f;
            part.set(base).translate(0, 23.5f * P, 0).rotateZ(-0.10f - capeSway).translate(0, -23.5f * P, 0);
            addBox(part, -2.9f * P, 16 * P, 0, 0.4f * P, 7.5f * P, 3.8f * P, extra);
        } else {
            addBox(base, -3.3f * P, 19 * P, 0, 1.2f * P, 3.6f * P, 3.0f * P, extra);
            addBox(base, -3.3f * P, 22.9f * P, 0, 0.9f * P, 0.5f * P, 2.0f * P, belt); // straps roll
        }

        // ---- Arms (shoulder pivot y = 23px), slightly proud of the torso ----
        for (int s = -1; s <= 1; s += 2) {
            float a = -swing * 0.8f * s + relax * s + idleSway * s * 0.5f;
            float az = s * 6.25f * P;
            part.set(base).translate(0, 23 * P, az).rotateZ(a).translate(0, -23 * P, -az);
            addBox(part, 0, 21 * P, az, 2.1f * P, 3 * P, 2.1f * P, tunic);
            addBox(part, 0, 15.5f * P, az, 1.9f * P, 3.0f * P, 1.9f * P, skin);
        }

        // ---- Head (neck pivot y = 24px), tracks the camera ----
        float headYaw = clamp(wrapAngle(camYawIn - bodyYaw), -1.0f, 1.0f);
        float headPitch = clamp(camPitchIn, -0.9f, 0.9f) * 0.8f;
        part.set(base)
                .translate(0, 24 * P, 0)
                .rotateY(-headYaw)
                .rotateZ(headPitch)
                .translate(0, -24 * P, 0);

        addBox(part, 0, 28 * P, 0, 4 * P, 4 * P, 4 * P, skin);
        // Hair cap + front fringe
        addBox(part, -0.4f * P, 31.4f * P, 0, 4.4f * P, 1.1f * P, 4.4f * P, hair);
        addBox(part, 3.6f * P, 30.4f * P, 0, 0.8f * P, 1.2f * P, 4.4f * P, hair);
        // Eyes on the front (+X) face
        addBox(part, 4.05f * P, 28.6f * P, 1.5f * P, 0.15f * P, 0.7f * P, 0.7f * P, eyes);
        addBox(part, 4.05f * P, 28.6f * P, -1.5f * P, 0.15f * P, 0.7f * P, 0.7f * P, eyes);

        if (elf) {
            // Pointed ears sweeping up the sides
            addBox(part, -0.5f * P, 30.2f * P, 4.5f * P, 0.6f * P, 1.5f * P, 0.5f * P, skin);
            addBox(part, -0.5f * P, 30.2f * P, -4.5f * P, 0.6f * P, 1.5f * P, 0.5f * P, skin);
            // Long hair falling down the back of the neck
            addBox(part, -3.4f * P, 23.5f * P, 0, 0.9f * P, 5.0f * P, 3.2f * P, hair);
        }
    }

    private static float[] rgb(float r, float g, float b) {
        return new float[]{r, g, b};
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : Math.min(v, hi);
    }

    /** Wrap an angle difference to [-PI, PI]. */
    private static float wrapAngle(float a) {
        while (a > Math.PI) a -= (float) (Math.PI * 2);
        while (a < -Math.PI) a += (float) (Math.PI * 2);
        return a;
    }

    /** Box centered at (cx,cy,cz), transformed by xf, with baked face shading. */
    private void addBox(
            Matrix4f xf,
            float cx, float cy, float cz,
            float hx, float hy, float hz,
            float[] color
    ) {
        // face order: +X -X +Y -Y +Z -Z (simple sun-from-above shading)
        final float[] faceShade = {0.85f, 0.72f, 1.00f, 0.50f, 0.78f, 0.78f};

        float x0 = cx - hx, x1 = cx + hx;
        float y0 = cy - hy, y1 = cy + hy;
        float z0 = cz - hz, z1 = cz + hz;

        float[][][] faces = {
                {{x1, y0, z0}, {x1, y1, z0}, {x1, y1, z1}, {x1, y0, z1}}, // +X
                {{x0, y0, z1}, {x0, y1, z1}, {x0, y1, z0}, {x0, y0, z0}}, // -X
                {{x0, y1, z0}, {x0, y1, z1}, {x1, y1, z1}, {x1, y1, z0}}, // +Y
                {{x0, y0, z1}, {x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}}, // -Y
                {{x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}, {x0, y1, z1}}, // +Z
                {{x1, y0, z0}, {x0, y0, z0}, {x0, y1, z0}, {x1, y1, z0}}, // -Z
        };

        ensureCapacity(6 * 6 * 6);

        int[][] tris = {{0, 1, 2}, {2, 3, 0}};
        for (int f = 0; f < 6; f++) {
            float s = faceShade[f];
            float fr = color[0] * s, fg = color[1] * s, fb = color[2] * s;
            float[][] q = faces[f];
            for (int[] tri : tris) {
                for (int idx : tri) {
                    vtx.set(q[idx][0], q[idx][1], q[idx][2]);
                    xf.transformPosition(vtx);
                    verts[floatCount++] = vtx.x;
                    verts[floatCount++] = vtx.y;
                    verts[floatCount++] = vtx.z;
                    verts[floatCount++] = fr;
                    verts[floatCount++] = fg;
                    verts[floatCount++] = fb;
                }
            }
        }
    }

    private void ensureCapacity(int add) {
        if (floatCount + add > verts.length) {
            verts = java.util.Arrays.copyOf(verts, Math.max(verts.length * 2, floatCount + add));
        }
    }
}
