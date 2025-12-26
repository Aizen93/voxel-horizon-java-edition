package org.aouessar.renderer.camera;

import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class Camera {

    public final Vector3f position = new Vector3f(0, 80, 0);

    // yaw/pitch in radians
    public float yaw = (float) Math.toRadians(90);
    public float pitch = 0f;

    public Matrix4f viewMatrix() {
        Vector3f forward = forwardDir();
        Vector3f center = new Vector3f(position).add(forward);
        return new Matrix4f().lookAt(position, center, new Vector3f(0, 1, 0));
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
}