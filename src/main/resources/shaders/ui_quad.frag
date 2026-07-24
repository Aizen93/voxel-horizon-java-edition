#version 330 core

in vec2 vUv;
in vec4 vColor;

uniform sampler2D uTex;

out vec4 FragColor;

void main() {
    // uv.x < -0.5 marks untextured (flat color) vertices
    vec4 tex = (vUv.x < -0.5) ? vec4(1.0) : texture(uTex, vUv);
    FragColor = tex * vColor;
}
