#version 330 core

in vec2 vUv;
uniform sampler2D uScene;
uniform float uThreshold;
out vec4 FragColor;

void main() {
    vec3 c = texture(uScene, vUv).rgb;
    float luma = dot(c, vec3(0.2126, 0.7152, 0.0722));
    // Soft knee around the threshold so bloom fades in smoothly
    float w = smoothstep(uThreshold - 0.15, uThreshold + 0.35, luma);
    FragColor = vec4(c * w, 1.0);
}
