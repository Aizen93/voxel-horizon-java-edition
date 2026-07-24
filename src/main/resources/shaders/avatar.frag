#version 330 core

// Player avatar: baked per-face shading in the vertex color, scaled by the
// local light (day/night sunlight x cave skylight + torch warmth).

in vec3 vColor;

uniform vec3 uLight;

out vec4 FragColor;

void main() {
    FragColor = vec4(vColor * uLight, 1.0);
}
