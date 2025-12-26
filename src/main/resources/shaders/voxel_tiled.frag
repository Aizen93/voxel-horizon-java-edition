#version 330 core

in vec2 vTileMin;
in vec2 vUvLocal;

uniform sampler2D uAtlas;
uniform vec2 uTileSize;

out vec4 FragColor;

void main() {
    vec2 uv = vTileMin + fract(vUvLocal) * uTileSize;
    FragColor = texture(uAtlas, uv);
}