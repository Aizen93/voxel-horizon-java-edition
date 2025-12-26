package org.aouessar.renderer.camera;

import static org.lwjgl.glfw.GLFW.*;

public final class CameraController {

    private final Camera camera;
    private final long window;

    private boolean firstMouse = true;
    private double lastX, lastY;

    public float moveSpeed = 25f;
    public float mouseSensitivity = 0.0025f;

    public CameraController(Camera camera, long window) {
        this.camera = camera;
        this.window = window;

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

    public void update(float dt) {
        if (glfwGetKey(window, GLFW_KEY_ESCAPE) == GLFW_PRESS) {
            glfwSetWindowShouldClose(window, true);
        }

        float spd = moveSpeed * dt;

        var forward = camera.forwardDir();
        var right = camera.rightDir();

        if (glfwGetKey(window, GLFW_KEY_W) == GLFW_PRESS) camera.position.fma(spd, forward);
        if (glfwGetKey(window, GLFW_KEY_S) == GLFW_PRESS) camera.position.fma(-spd, forward);
        if (glfwGetKey(window, GLFW_KEY_D) == GLFW_PRESS) camera.position.fma(spd, right);
        if (glfwGetKey(window, GLFW_KEY_A) == GLFW_PRESS) camera.position.fma(-spd, right);

        if (glfwGetKey(window, GLFW_KEY_SPACE) == GLFW_PRESS) camera.position.y += spd;
        if (glfwGetKey(window, GLFW_KEY_LEFT_SHIFT) == GLFW_PRESS) camera.position.y -= spd;
    }
}