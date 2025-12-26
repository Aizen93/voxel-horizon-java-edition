#version 330 core

in vec2 vUv;
out vec4 FragColor;

uniform sampler2D uAtlas;

void main() {
    FragColor = texture(uAtlas, vUv);
    // optional: kill fully transparent pixels (if you ever add transparent tiles)
    // if (FragColor.a < 0.1) discard;
}