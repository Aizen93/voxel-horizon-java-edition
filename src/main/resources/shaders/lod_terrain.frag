#version 330 core

flat in vec3 vColor;
in vec3 vNormal;
in vec3 vWorldPos;

uniform vec3  uCameraPos;
uniform vec3  uFogColor;
uniform vec3  uSunDir;

// Same altitude-based fog model as voxel_tiled.frag
uniform float uFogAltBase;
uniform float uFogAltRange;
uniform float uFogStartLow;
uniform float uFogStartHigh;
uniform float uFogRangeLow;
uniform float uFogRangeHigh;
uniform float uFogStartMul;
uniform float uFogRangeMul;

// Chebyshev distance below which LOD fragments are discarded — the near-field
// voxel chunks own that area.
uniform float uLodNearCut;

uniform float uTime;
uniform float uCloudCover;
uniform float uCloudHeight;
uniform float uCloudShadowStrength;

// Cascaded sun shadow maps: the LOD mostly reads cascades 1-2, which are fed
// by both near chunks and LOD casters, so mountains shade valleys at 1km+.
uniform sampler2DShadow uShadowMap0;
uniform sampler2DShadow uShadowMap1;
uniform sampler2DShadow uShadowMap2;
uniform mat4  uLightMVP0;
uniform mat4  uLightMVP1;
uniform mat4  uLightMVP2;
uniform vec3  uCascadeSplits;
uniform vec3  uCascadeBias;
uniform float uShadowStrength;
uniform float uShadowTexel;

uniform float uUnderwater;
uniform vec3  uUnderwaterColor;

// Day/night sunlight (intensity + color) from FogCycle
uniform vec3  uSunLight;

out vec4 FragColor;

float hash21(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float vnoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash21(i);
    float b = hash21(i + vec2(1.0, 0.0));
    float c = hash21(i + vec2(0.0, 1.0));
    float d = hash21(i + vec2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

float fbm(vec2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 4; i++) {
        v += a * vnoise(p);
        p = p * 2.13 + 17.31;
        a *= 0.5;
    }
    return v;
}

float cloudShadow(vec3 wp) {
    if (uCloudShadowStrength <= 0.001 || uCloudCover < 0.01 || uSunDir.y <= 0.05) return 1.0;
    vec2 p = wp.xz + uSunDir.xz * ((uCloudHeight - wp.y) / max(uSunDir.y, 0.2));
    float f = fbm(p * 0.0016 + vec2(uTime * 0.009, uTime * 0.0035));
    float threshold = mix(0.80, 0.44, uCloudCover);
    float cov = smoothstep(threshold, threshold + 0.24, f);
    return 1.0 - cov * uCloudShadowStrength * smoothstep(0.05, 0.2, uSunDir.y);
}

// Inline per-cascade sampling: sampler function parameters break the
// hardware compare mode on some drivers (see voxel_tiled.frag).
float sunShadow(vec3 worldPos) {
    if (uShadowStrength <= 0.001) return 1.0;
    float d = length(worldPos.xz - uCameraPos.xz);

    float sum = 0.0;
    if (d < uCascadeSplits.x) {
        vec4 ls = uLightMVP0 * vec4(worldPos, 1.0);
        vec3 p = ls.xyz / ls.w * 0.5 + 0.5;
        if (p.z >= 1.0) return 1.0;
        p.z -= uCascadeBias.x;
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = -1; dy <= 1; dy++)
                sum += texture(uShadowMap0, vec3(p.xy + vec2(dx, dy) * uShadowTexel, p.z));
        return sum / 9.0;
    }
    if (d < uCascadeSplits.y) {
        vec4 ls = uLightMVP1 * vec4(worldPos, 1.0);
        vec3 p = ls.xyz / ls.w * 0.5 + 0.5;
        if (p.z >= 1.0) return 1.0;
        p.z -= uCascadeBias.y;
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = -1; dy <= 1; dy++)
                sum += texture(uShadowMap1, vec3(p.xy + vec2(dx, dy) * uShadowTexel, p.z));
        return sum / 9.0;
    }
    vec4 ls = uLightMVP2 * vec4(worldPos, 1.0);
    vec3 p = ls.xyz / ls.w * 0.5 + 0.5;
    if (p.z >= 1.0) return 1.0;
    p.z -= uCascadeBias.z;
    for (int dx = -1; dx <= 1; dx++)
        for (int dy = -1; dy <= 1; dy++)
            sum += texture(uShadowMap2, vec3(p.xy + vec2(dx, dy) * uShadowTexel, p.z));
    return mix(sum / 9.0, 1.0, smoothstep(uCascadeSplits.z * 0.88, uCascadeSplits.z, d));
}

void main() {
    // Euclidean cut: matches the circular dissolve band of the near field
    if (length(vWorldPos.xz - uCameraPos.xz) < uLodNearCut) discard;

    vec3 n = normalize(vNormal);

    // Match the near-field look: flat tops bright like PY faces, slopes fall
    // toward the side-face shade. A soft sun-facing term adds relief.
    float topness = clamp(n.y, 0.0, 1.0);
    float shade = mix(0.62, 1.0, topness * topness);

    float sunFace = dot(n, normalize(uSunDir));
    shade *= 0.90 + 0.10 * clamp(sunFace, -1.0, 1.0);

    // Cascaded sun shadows + drifting cloud shadows keep the far field alive
    float sh = sunShadow(vWorldPos) * cloudShadow(vWorldPos);
    shade *= mix(1.0, mix(0.60, 1.0, sh), max(uShadowStrength, 0.35) * 0.8);

    vec3 color = vColor * shade * uSunLight;

    float dist = length(vWorldPos - uCameraPos);

    if (uUnderwater > 0.5) {
        float uwf = 1.0 - exp(-dist * 0.06);
        color = mix(color, uUnderwaterColor, uwf);
    } else {
        float camAlt = uCameraPos.y;
        float altT = clamp((camAlt - uFogAltBase) / max(uFogAltRange, 0.0001), 0.0, 1.0);
        float fogStart = mix(uFogStartLow, uFogStartHigh, altT) * uFogStartMul;
        float fogRange = mix(uFogRangeLow, uFogRangeHigh, altT) * uFogRangeMul;
        float fog = clamp((dist - fogStart) / max(fogRange, 0.0001), 0.0, 1.0);
        color = mix(color, uFogColor, fog);
    }

    FragColor = vec4(color, 1.0);
}
