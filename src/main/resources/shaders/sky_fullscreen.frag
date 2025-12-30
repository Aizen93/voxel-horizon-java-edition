#version 330 core
in vec2 vUv;
out vec4 FragColor;

uniform vec3  uSkyTopColor;    // usually day/night fog color (possibly boosted)
uniform vec3  uSkyHorizonColor; // warm color (orange-ish)
uniform float uTwilight01;     // 0..1
uniform float uHorizonStrength; // 0..1 (how strong the band is)
uniform float uHorizonY;       // 0..1 (where the horizon band sits)
uniform float uHorizonSoftness; // 0.01..0.5

void main() {
    float y = clamp(vUv.y, 0.0, 1.0);

    // Horizon band mask (1 near horizon, 0 away)
    float d = abs(y - uHorizonY);
    float band = 1.0 - smoothstep(0.0, uHorizonSoftness, d);

    // Only show horizon glow during twilight
    band *= clamp(uTwilight01, 0.0, 1.0);

    // Mix top sky with warm horizon band
    vec3 col = uSkyTopColor;
    col = mix(col, uSkyHorizonColor, band * uHorizonStrength);

    // A small “exposure” lift so sunset feels bright (only twilight)
    col *= (1.0 + band * 0.35);

    FragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}