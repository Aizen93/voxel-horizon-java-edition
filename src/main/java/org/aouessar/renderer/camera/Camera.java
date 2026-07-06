package org.aouessar.renderer.camera;

import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class Camera {

    /** The PLAYER's eye position (physics moves this). */
    public final Vector3f position = new Vector3f(0, 180, 0);

    // yaw/pitch in radians
    public float yaw = (float) Math.toRadians(90);
    public float pitch = 0f;

    /**
     * View mode, Minecraft F5 style: first person, third person from behind,
     * or third person facing the player. The orbit distance is set per frame
     * by the controller (pulled in when terrain is in the way).
     * {@link #position} always stays the player's eye.
     */
    public static final int VIEW_FIRST = 0;
    public static final int VIEW_THIRD_BACK = 1;
    public static final int VIEW_THIRD_FRONT = 2;

    public int viewMode = VIEW_FIRST;
    public float thirdPersonDist = 4f;

    public boolean isThirdPerson() {
        return viewMode != VIEW_FIRST;
    }

    /** The actual render viewpoint (player eye, or the orbit camera). */
    public Vector3f eyePosition(Vector3f dest) {
        if (viewMode == VIEW_FIRST) return dest.set(position);
        float sign = (viewMode == VIEW_THIRD_BACK) ? -1f : 1f;
        return forwardDir().mul(sign * thirdPersonDist, dest).add(position);
    }

    public Matrix4f viewMatrix() {
        Vector3f eye = eyePosition(new Vector3f());
        Vector3f dir = forwardDir();
        if (viewMode == VIEW_THIRD_FRONT) dir.negate();
        Vector3f center = eye.add(dir, new Vector3f());
        return new Matrix4f().lookAt(eye, center, new Vector3f(0, 1, 0));
    }

    public Vector3f forwardDir() {
        float cy = (float) Math.cos(yaw);
        float sy = (float) Math.sin(yaw);
        float cp = (float) Math.cos(pitch);
        float sp = (float) Math.sin(pitch);

        // right-handed: forward on XZ with yaw, pitch on Y
        return new Vector3f(cy * cp, sp, sy * cp).normalize();
    }

    public Vector3f rightDir() {
        return forwardDir().cross(0, 1, 0, new Vector3f()).normalize();
    }

    public void setPosition(float x, float y, float z) {
        this.position.set(x, y, z);
    }
}