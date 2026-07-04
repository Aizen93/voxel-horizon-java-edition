#version 330 core

flat in vec3 vColor;
in vec3 vNormal;
in vec3 vWorldPos;

uniform vec3  uCameraPos;
uniform vec3  uFogColor;
uniform vec3  uSunDir;
uniform vec3  uSkyTopColor;
uniform vec3  uSkyHorizonColor;

uniform float uFogAltBase;
uniform float uFogAltRange;
uniform float uFogStartLow;
uniform float uFogStartHigh;
uniform float uFogRangeLow;
uniform float uFogRangeHigh;
uniform float uFogStartMul;
uniform float uFogRangeMul;

uniform float uLodNearCut;
uniform float uTime;

uniform float uUnderwater;
uniform vec3  uUnderwaterColor;

// Day/night sunlight (intensity + color) from FogCycle
uniform vec3  uSunLight;

out vec4 FragColor;

void main() {
    // Euclidean cut: matches the circular dissolve band of the near field
    if (length(vWorldPos.xz - uCameraPos.xz) < uLodNearCut) discard;

    vec3 viewDir = normalize(uCameraPos - vWorldPos);

    // Gentle animated swell so the far ocean carries a live sun-glitter path
    vec2 pw = vWorldPos.xz;
    float g1 = cos(dot(pw, vec2(0.131, 0.093)) + uTime * 0.9);
    float g2 = cos(dot(pw, vec2(-0.071, 0.117)) + uTime * 1.3);
    vec3 n = normalize(vec3(g1 * 0.055 + g2 * 0.030, 1.0, g1 * 0.038 - g2 * 0.050));

    float cosTheta = clamp(dot(viewDir, n), 0.0, 1.0);
    float fresnel = 0.02 + 0.98 * pow(1.0 - cosTheta, 5.0);

    // Sky reflection about the wavy normal
    vec3 reflectDir = reflect(-viewDir, n);
    float skyT = clamp(reflectDir.y, 0.0, 1.0);
    vec3 skyColor = mix(uSkyHorizonColor, uSkyTopColor, skyT);

    // Sun glint (Blinn-Phong), gated on sun above horizon
    vec3 h = normalize(normalize(uSunDir) + viewDir);
    float sunGate = smoothstep(0.0, 0.15, uSunDir.y);
    float spec = (pow(max(dot(n, h), 0.0), 160.0) * 1.1
                + pow(max(dot(n, h), 0.0), 32.0) * 0.18) * sunGate;

    vec3 base = vColor * 0.85 * uSunLight;
    vec3 color = mix(base, skyColor, fresnel * 0.85) + spec * vec3(1.0, 0.97, 0.88);

    float dist = length(vWorldPos - uCameraPos);
    float fog;

    if (uUnderwater > 0.5) {
        // LOD water starts far beyond the underwater murk: sink it fully
        fog = clamp(1.0 - exp(-dist * 0.06), 0.0, 1.0);
        color = mix(color, uUnderwaterColor, fog);
    } else {
        float camAlt = uCameraPos.y;
        float altT = clamp((camAlt - uFogAltBase) / max(uFogAltRange, 0.0001), 0.0, 1.0);
        float fogStart = mix(uFogStartLow, uFogStartHigh, altT) * uFogStartMul;
        float fogRange = mix(uFogRangeLow, uFogRangeHigh, altT) * uFogRangeMul;
        fog = clamp((dist - fogStart) / max(fogRange, 0.0001), 0.0, 1.0);
        color = mix(color, uFogColor, fog);
    }

    float alpha = mix(0.78, 0.94, fresnel);
    // Let fog flatten the alpha too so the horizon blends into the sky
    alpha = mix(alpha, 1.0, fog * 0.5);

    FragColor = vec4(color, alpha);
}
