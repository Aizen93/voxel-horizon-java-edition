#version 330 core

in vec2 vTileMin;
in vec2 vUvLocal;

uniform sampler2D uAtlas;
uniform vec2 uTileSize;

void main() {
    // Alpha test so leaves cast leafy shadows instead of solid slabs.
    // Opaque tiles have alpha = 1, so one program covers opaque + cutout.
    vec4 tex = texture(uAtlas, vTileMin + fract(vUvLocal) * uTileSize);
    if (tex.a < 0.5) discard;
    // depth-only: no color output
}
