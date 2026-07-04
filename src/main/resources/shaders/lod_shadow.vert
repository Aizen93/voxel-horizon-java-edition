#version 330 core

// Depth-only pass for LOD terrain meshes (far shadow cascades).
// Only the position attribute of the 9-float LOD layout is read.

layout (location = 0) in vec3 aPos;

uniform mat4 uLightMVP;

void main() {
    gl_Position = uLightMVP * vec4(aPos, 1.0);
}
