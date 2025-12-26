#version 330 core

layout (location = 0) in vec3 aPos;
layout (location = 1) in vec2 aTileMin;
layout (location = 2) in vec2 aUvLocal;

uniform mat4 uMVP;

out vec2 vTileMin;
out vec2 vUvLocal;

void main() {
    vTileMin = aTileMin;
    vUvLocal = aUvLocal;
    gl_Position = uMVP * vec4(aPos, 1.0);
}