#version 330 core

layout (location = 0) in vec3 aPos;
layout (location = 1) in vec3 aColor;
layout (location = 2) in vec3 aNormal;

uniform mat4 uMVP;

// Flat color => each triangle keeps one solid color, which reads as
// "blocky Minecraft terrain at a distance" instead of a blurred gradient.
flat out vec3 vColor;
out vec3 vNormal;
out vec3 vWorldPos;

void main() {
    vColor = aColor;
    vNormal = aNormal;
    vWorldPos = aPos;
    gl_Position = uMVP * vec4(aPos, 1.0);
}
