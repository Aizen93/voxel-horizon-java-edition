#version 330 core

in vec2 vTileMin;
in vec2 vUvLocal;
in vec3 vWorldPos;
in float vShade;

uniform sampler2D uAtlas;
uniform vec2 uTileSize;

// Fog controls (set from Java)
uniform vec3  uCameraPos;
uniform vec3  uFogColor;

uniform float uFogAltBase;     // 80.0 in your snippet
uniform float uFogAltRange;    // 400.0 in your snippet
uniform float uFogStartLow;    // 1400.0
uniform float uFogStartHigh;   // 700.0
uniform float uFogRangeLow;    // 3600.0
uniform float uFogRangeHigh;   // 2200.0
uniform float uFogStartMul;   // 1.0 normally, <1 tightens fog (rain/mist)
uniform float uFogRangeMul;   // 1.0 normally, <1 tightens fog (rain/mist)

// Near-field dissolve band: the voxel world melts away pixel-by-pixel across
// [uNearFadeStart, uNearFadeEnd] while the LOD is already drawn underneath,
// so there is no hard near/far switch line.
uniform float uNearFadeStart;
uniform float uNearFadeEnd;

// Cascaded sun shadow maps: sharp close-ups (0), the near field (1), and the
// far field out to ~1300 blocks (2, fed by LOD casters too).
uniform sampler2DShadow uShadowMap0;
uniform sampler2DShadow uShadowMap1;
uniform sampler2DShadow uShadowMap2;
uniform mat4  uLightMVP0;
uniform mat4  uLightMVP1;
uniform mat4  uLightMVP2;
uniform vec3  uCascadeSplits; // XZ distance where each cascade ends
uniform vec3  uCascadeBias;   // NDC depth bias per cascade
uniform float uShadowStrength; // 0 = off (night), ~0.9 = full daytime shadows
uniform float uShadowTexel;

// Cloud shadows (matches the sky's cloud field)
uniform vec3  uSunDir;
uniform float uTime;
uniform float uCloudCover;
uniform float uCloudHeight;
uniform float uCloudShadowStrength;

// Underwater rendering: uWaterY = render-space Y of the water surface
uniform float uUnderwater;
uniform float uWaterY;
uniform vec3  uUnderwaterColor;

// Day/night sunlight (intensity + color) from FogCycle
uniform vec3  uSunLight;

// Handheld torch: warm point light around the camera (0 = off)
uniform vec3  uTorchPos;
uniform float uTorchLight;
uniform vec3  uTorchColor;
uniform float uTorchRange;

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
    // Project along the sun onto the cloud layer; same field as the sky shader
    vec2 p = wp.xz + uSunDir.xz * ((uCloudHeight - wp.y) / max(uSunDir.y, 0.2));
    float f = fbm(p * 0.0016 + vec2(uTime * 0.009, uTime * 0.0035));
    float threshold = mix(0.80, 0.44, uCloudCover);
    float cov = smoothstep(threshold, threshold + 0.24, f);
    return 1.0 - cov * uCloudShadowStrength * smoothstep(0.05, 0.2, uSunDir.y);
}

float mcShade(vec3 n) {
    vec3 a = abs(n);
    if (a.y >= a.x && a.y >= a.z) return (n.y > 0.0) ? 1.00 : 0.55;
    if (a.x >= a.z) return 0.80;
    return 0.70;
}

float bayer4(vec2 fc) {
    const float m[16] = float[16](0.0, 8.0, 2.0, 10.0, 12.0, 4.0, 14.0, 6.0,
                                  3.0, 11.0, 1.0, 9.0, 15.0, 7.0, 13.0, 5.0);
    int i = int(mod(fc.x, 4.0)) + int(mod(fc.y, 4.0)) * 4;
    return (m[i] + 0.5) / 16.0;
}

// Samplers must be indexed statically in GLSL 330, and passing them as
// function parameters silently breaks the compare mode on some drivers —
// so each cascade samples its own map inline.
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
    // Fade to lit at the far cascade edge instead of a hard shadow line
    return mix(sum / 9.0, 1.0, smoothstep(uCascadeSplits.z * 0.88, uCascadeSplits.z, d));
}

// Animated caustic light on submerged terrain: two drifting noise layers,
// ridged and sharpened so bright filaments dance where they align.
float caustic(vec2 p, float t) {
    float n1 = vnoise(p * 0.55 + vec2(t * 0.11, t * 0.13));
    float n2 = vnoise(p * 0.80 - vec2(t * 0.14, t * 0.07));
    float v = clamp(1.0 - abs(n1 + n2 - 1.0), 0.0, 1.0);
    return pow(v, 7.0);
}

void main() {
    // Dissolve into the far-field LOD across the fade band
    float distXZ = length(vWorldPos.xz - uCameraPos.xz);
    float fade = smoothstep(uNearFadeStart, uNearFadeEnd, distXZ);
    if (fade > 0.0 && bayer4(gl_FragCoord.xy) < fade) discard;

    vec2 uv = vTileMin + fract(vUvLocal) * uTileSize;
    // Gradients from the CONTINUOUS repeat-space coords: fract() has a huge
    // derivative at tile wraps, which would otherwise force the smallest mip
    // and draw seam lines across greedy-merged quads.
    vec4 tex = textureGrad(uAtlas, uv, dFdx(vUvLocal) * uTileSize, dFdy(vUvLocal) * uTileSize);

    vec3 n = normalize(cross(dFdx(vWorldPos), dFdy(vWorldPos)));

    // Sun shadow darkens the direct-light share only; cloud shadows drift over
    float sh = sunShadow(vWorldPos) * cloudShadow(vWorldPos);
    float shadowMul = mix(1.0, mix(0.55, 1.0, sh), max(uShadowStrength, 0.35));

    vec3 color = tex.rgb * mcShade(n) * vShade * shadowMul * uSunLight;

    // Caustic shimmer on anything under the water surface (visible from
    // above through the refracting water, and while diving)
    float below = uWaterY - vWorldPos.y;
    if (below > 0.02 && uSunDir.y > 0.05) {
        float cfade = exp(-below * 0.10);
        float c = caustic(vWorldPos.xz, uTime);
        color *= 1.0 + c * 1.4 * cfade * smoothstep(0.05, 0.2, uSunDir.y) * sh;
    }

    // Handheld torch: quadratic falloff + soft diffuse toward the flame,
    // independent of sun/sky light so it cuts through cave darkness.
    if (uTorchLight > 0.001) {
        vec3 toTorch = uTorchPos - vWorldPos;
        float td = length(toTorch);
        float fall = clamp(1.0 - td / uTorchRange, 0.0, 1.0);
        fall *= fall;
        float ndl = 0.55 + 0.45 * max(dot(n, toTorch / max(td, 0.001)), 0.0);
        color += tex.rgb * uTorchColor * (uTorchLight * fall * ndl);
    }

    float dist = length(vWorldPos - uCameraPos);

    if (uUnderwater > 0.5) {
        // Dense scattering fog inside the water volume
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

    FragColor = vec4(color, tex.a);
}
