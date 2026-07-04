#version 330 core

layout (location = 0) in vec3 aPos;
layout (location = 1) in vec2 aTileMin;
layout (location = 2) in vec2 aUvLocal;
layout (location = 3) in float aShade; // per-vertex AO x skylight (0..1)

uniform mat4 uMVP;

out vec2 vTileMin;
out vec2 vUvLocal;
out vec3 vWorldPos;
out float vShade;

void main() {
    vTileMin = aTileMin;
    vUvLocal = aUvLocal;
    vWorldPos = aPos;                  // world-space position (as built by mesher)
    vShade = aShade;
    gl_Position = uMVP * vec4(aPos, 1.0);
}
