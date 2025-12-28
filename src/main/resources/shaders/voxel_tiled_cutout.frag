#version 330 core

in vec2 vTileMin;
in vec2 vUvLocal;

uniform sampler2D uAtlas;
uniform vec2 uTileSize;

// Optional, but future-proof
uniform float uAlphaCutoff = 0.5;

out vec4 FragColor;

void main() {
    vec2 uv = vTileMin + fract(vUvLocal) * uTileSize;
    vec4 tex = texture(uAtlas, uv);

    // Alpha test (cutout)
    if (tex.a < uAlphaCutoff)
        discard;

    FragColor = tex;
}