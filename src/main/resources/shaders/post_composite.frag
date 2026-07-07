#version 330 core

in vec2 vUv;
uniform sampler2D uScene;
uniform sampler2D uBloom;
uniform sampler2D uRays;
uniform float uExposure;
uniform float uBloomStrength;
uniform vec3  uRayColor;
uniform float uTime;
uniform float uUnderwater;
uniform float uLightning; // whole-sky flash, decays after each strike
uniform sampler2D uSSAO;  // blurred screen-space ambient occlusion
uniform float uSSAOOn;
uniform sampler2D uVol;   // volumetric sun shafts (black when inactive)
out vec4 FragColor;

// ACES filmic tonemap (Narkowicz fit) — soft highlight rolloff, rich mids
vec3 aces(vec3 x) {
    return clamp((x * (2.51 * x + 0.03)) / (x * (2.43 * x + 0.59) + 0.14), 0.0, 1.0);
}

void main() {
    vec2 uv = vUv;

    // Underwater: gentle refraction wobble of the whole image
    if (uUnderwater > 0.5) {
        uv += vec2(sin(uv.y * 28.0 + uTime * 2.1),
                   cos(uv.x * 24.0 + uTime * 1.7)) * 0.0022;
    }

    vec3 col = texture(uScene, uv).rgb;

    // SSAO: contact shadows in creases/corners (sky returns 1.0)
    if (uSSAOOn > 0.5) col *= texture(uSSAO, uv).r;

    col += texture(uBloom, uv).rgb * uBloomStrength;
    col += texture(uRays, uv).rgb * uRayColor;
    col += texture(uVol, uv).rgb;

    // Lightning: cold white flash over the whole frame (pre-tonemap)
    col += uLightning * vec3(0.55, 0.60, 0.72);

    col = aces(col * uExposure);

    if (uUnderwater > 0.5) {
        col *= vec3(0.82, 0.96, 1.06);
        float vig = smoothstep(1.30, 0.45, length(vUv - 0.5) * 2.0);
        col *= mix(0.55, 1.0, vig);
    }

    FragColor = vec4(col, 1.0);
}
