#version 330 core

// Water v3: refraction of the rendered scene, Beer's-law depth absorption,
// screen-space reflections with sky fallback, animated ripple normals,
// shore foam and a sun glitter path.

in vec2 vTileMin;
in vec2 vUvLocal;
in vec3 vWorldPos;
in float vShade;

uniform float uTime;

uniform vec3  uCameraPos;
uniform vec3  uFogColor;

uniform float uFogAltBase;
uniform float uFogAltRange;
uniform float uFogStartLow;
uniform float uFogStartHigh;
uniform float uFogRangeLow;
uniform float uFogRangeHigh;
uniform float uFogStartMul;
uniform float uFogRangeMul;

uniform vec3  uSkyTopColor;
uniform vec3  uSkyHorizonColor;
uniform vec3  uSunDir;

uniform float uNearFadeStart;
uniform float uNearFadeEnd;

// Post pipeline inputs: opaque scene resolve (color + depth)
uniform sampler2D uSceneColor;
uniform sampler2D uSceneDepth;
uniform vec2  uScreenSize;
uniform float uNearPlane;
uniform float uFarPlane;
uniform mat4  uMVP;

uniform float uUnderwater;
uniform vec3  uUnderwaterColor;

// Day/night sunlight: the refracted scene is already light-scaled (it is the
// rendered world), but additive terms must not glow at night.
uniform vec3  uSunLight;

// Handheld torch: warm point light around the camera (0 = off)
uniform vec3  uTorchPos;
uniform float uTorchLight;
uniform vec3  uTorchColor;
uniform float uTorchRange;

out vec4 FragColor;

float bayer4(vec2 fc) {
    const float m[16] = float[16](0.0, 8.0, 2.0, 10.0, 12.0, 4.0, 14.0, 6.0,
                                  3.0, 11.0, 1.0, 9.0, 15.0, 7.0, 13.0, 5.0);
    int i = int(mod(fc.x, 4.0)) + int(mod(fc.y, 4.0)) * 4;
    return (m[i] + 0.5) / 16.0;
}

// Layered ripple normal: broad swell + fine wind ripples (the "Bliss" look)
vec3 waterNormal(vec3 worldPos, float time) {
    vec2 pos = worldPos.xz;
    vec2 gradient = vec2(0.0);

    const vec2[6] dirs = vec2[6](
        normalize(vec2(1.0, 0.6)),
        normalize(vec2(-0.7, 1.0)),
        normalize(vec2(0.5, -0.8)),
        normalize(vec2(-1.0, -0.3)),
        normalize(vec2(0.8, 0.9)),
        normalize(vec2(-0.4, 0.7))
    );
    const float[6] freqs = float[6](0.13, 0.31, 0.74, 1.45, 2.9, 4.6);
    const float[6] amps  = float[6](0.10, 0.06, 0.030, 0.018, 0.011, 0.007);
    const float[6] speeds = float[6](0.45, 0.75, 1.1, 1.5, 2.1, 2.7);

    for (int i = 0; i < 6; i++) {
        float phase = dot(pos, dirs[i]) * freqs[i] + time * speeds[i];
        gradient += dirs[i] * cos(phase) * freqs[i] * amps[i];
    }

    return normalize(vec3(-gradient.x, 1.0, -gradient.y));
}

float fresnelSchlick(float cosTheta, float F0) {
    return F0 + (1.0 - F0) * pow(1.0 - cosTheta, 5.0);
}

float linZ(float d) {
    float n = uNearPlane;
    float f = uFarPlane;
    return (2.0 * n * f) / (f + n - (2.0 * d - 1.0) * (f - n));
}

float edgeFade(vec2 uv) {
    vec2 f = smoothstep(vec2(0.0), vec2(0.08), uv) * (1.0 - smoothstep(vec2(0.92), vec2(1.0), uv));
    return f.x * f.y;
}

void main() {
    float distXZ = length(vWorldPos.xz - uCameraPos.xz);
    float fade = smoothstep(uNearFadeStart, uNearFadeEnd, distXZ);
    if (fade > 0.0 && bayer4(gl_FragCoord.xy) < fade) discard;

    vec3 viewDir = normalize(uCameraPos - vWorldPos);
    vec3 normal = waterNormal(vWorldPos, uTime);
    float NdotV = max(dot(normal, viewDir), 0.0);
    float fresnel = fresnelSchlick(NdotV, 0.02);

    vec2 screenUv = gl_FragCoord.xy / uScreenSize;
    float pixelZ = linZ(gl_FragCoord.z);
    float viewDist = length(vWorldPos - uCameraPos);

    bool isTopFace = vShade > 1.001;

    // Baked cave/canopy skylight for this water face (from the mesher):
    // side faces carry it directly (0..1), top faces carry (1 + light).
    // Water deep in caves must not reflect the sky or sparkle in the sun.
    float light01 = clamp(isTopFace ? vShade - 1.0 : vShade, 0.0, 1.0);

    // ------------------------------------------------------------------
    // UNDERWATER: looking up at the surface from inside the volume.
    // Snell's window: the world above shows through a bright cone overhead;
    // grazing angles turn into a silvery internal reflection.
    // ------------------------------------------------------------------
    if (uUnderwater > 0.5) {
        vec2 ruv = screenUv + normal.xz * 0.09;
        float rd = texture(uSceneDepth, ruv).r;
        if (linZ(rd) < pixelZ - 0.15) ruv = screenUv;
        vec3 above = texture(uSceneColor, ruv).rgb;

        float cosUp = abs(dot(viewDir, vec3(0.0, 1.0, 0.0)));
        float window = smoothstep(0.50, 0.75, cosUp);

        vec3 halfUp = normalize(normalize(uSunDir) + viewDir);
        float specUp = pow(max(dot(normal, halfUp), 0.0), 90.0)
                * smoothstep(0.0, 0.2, uSunDir.y) * 0.8;

        vec3 rgb = mix(uUnderwaterColor * (0.25 + 1.35 * light01), above * vec3(0.90, 0.97, 1.0), window)
                + vec3(specUp) * light01;

        float uwf = 1.0 - exp(-viewDist * 0.06);
        rgb = mix(rgb, uUnderwaterColor, uwf);

        // Torch glow on the surface seen from below (flooded caves)
        if (uTorchLight > 0.001) {
            float tf = clamp(1.0 - distance(vWorldPos, uTorchPos) / uTorchRange, 0.0, 1.0);
            rgb += uTorchColor * (uTorchLight * tf * tf * 0.25);
        }
        FragColor = vec4(rgb, 1.0);
        return;
    }

    // ------------------------------------------------------------------
    // REFRACTION: sample the opaque scene behind the water with a wavy
    // offset; reject samples that would pull foreground geometry over us
    // ------------------------------------------------------------------
    float refrScale = 0.06 * clamp(6.0 / max(viewDist * 0.25, 1.0), 0.08, 1.0);
    vec2 refrUv = screenUv + normal.xz * refrScale;

    float refrD = texture(uSceneDepth, refrUv).r;
    if (linZ(refrD) < pixelZ - 0.15) {
        refrUv = screenUv; // refracted sample is in front of the water: keep straight
        refrD = texture(uSceneDepth, refrUv).r;
    }
    vec3 refracted = texture(uSceneColor, refrUv).rgb;

    // Water travel distance (view-space) -> Beer's law absorption
    float sceneZ = linZ(refrD);
    float travel = max(sceneZ - pixelZ, 0.0);

    vec3 absorb = exp(-vec3(0.42, 0.15, 0.10) * travel);
    vec3 scatter = vec3(0.06, 0.20, 0.22) * (1.0 - exp(-travel * 0.30)) * uSunLight * light01;
    vec3 underwater = refracted * absorb + scatter;

    // ------------------------------------------------------------------
    // REFLECTION: screen-space ray march with sky-gradient fallback
    // ------------------------------------------------------------------
    // Calmer normal for the reflection ray keeps mirrored trees readable
    vec3 reflNormal = normalize(vec3(normal.x * 0.45, 1.0, normal.z * 0.45));
    vec3 reflectDir = reflect(-viewDir, reflNormal);

    float skyT = pow(clamp(reflectDir.y * 0.5 + 0.5, 0.0, 1.0), 0.6);
    vec3 skyReflection = mix(uSkyHorizonColor, uSkyTopColor, skyT);
    skyReflection = mix(skyReflection, uSkyHorizonColor * 1.1, exp(-abs(reflectDir.y) * 3.0) * 0.3);
    // No sky to reflect inside caves: the fallback dims with the baked light.
    // (Screen-space hits keep their own brightness — a dark cave reflects dark.)
    skyReflection *= light01;

    vec3 reflection = skyReflection;
    if (reflectDir.y > -0.4) {
        float t = 0.6;
        float hit = 0.0;
        for (int i = 0; i < 26; i++) {
            vec3 p = vWorldPos + reflectDir * t;
            vec4 clip = uMVP * vec4(p, 1.0);
            if (clip.w <= 0.0) break;
            vec3 ndc = clip.xyz / clip.w;
            vec2 suv = ndc.xy * 0.5 + 0.5;
            if (suv.x < 0.0 || suv.x > 1.0 || suv.y < 0.0 || suv.y > 1.0) break;

            float sd = texture(uSceneDepth, suv).r;
            float sZ = linZ(sd);
            float rZ = linZ(ndc.z * 0.5 + 0.5);
            if (sZ < rZ - 0.05 && (rZ - sZ) < 2.5 + t * 0.10) {
                reflection = texture(uSceneColor, suv).rgb;
                hit = edgeFade(suv);
                break;
            }
            t *= 1.32;
        }
        reflection = mix(skyReflection, reflection, hit);
    }

    // ------------------------------------------------------------------
    // Sun glitter + foam + combine
    // ------------------------------------------------------------------
    vec3 halfVec = normalize(normalize(uSunDir) + viewDir);
    float NdotH = max(dot(normal, halfVec), 0.0);
    vec3 sunSpecular = vec3(1.0, 0.98, 0.9)
            * (pow(NdotH, 380.0) * 2.2 + pow(NdotH, 64.0) * 0.22)
            * smoothstep(-0.1, 0.2, uSunDir.y) * light01;

    vec3 rgb;
    if (isTopFace) {
        rgb = mix(underwater, reflection, clamp(fresnel * 1.9, 0.04, 0.85)) + sunSpecular;

        // Foam where the water is shallow, flickering with the ripples
        float foam = smoothstep(1.3, 0.15, travel)
                * (0.22 + 0.30 * (0.5 + 0.5 * sin(uTime * 2.2 + vWorldPos.x * 1.9
                                                  + vWorldPos.z * 2.6 + normal.x * 40.0)));
        rgb += vec3(0.75, 0.80, 0.82) * foam * uSunLight * light01;
    } else {
        // Side faces: simple absorbed view into the volume
        vec3 tinted = texture(uSceneColor, screenUv).rgb * exp(-vec3(0.42, 0.15, 0.10) * 3.0)
                + vec3(0.06, 0.20, 0.22) * uSunLight * light01;
        rgb = mix(tinted, skyReflection, fresnel * 0.5) + sunSpecular * 0.4;
    }

    // Handheld torch: gentle warm glow on nearby water (cave pools)
    if (uTorchLight > 0.001) {
        float tf = clamp(1.0 - distance(vWorldPos, uTorchPos) / uTorchRange, 0.0, 1.0);
        rgb += uTorchColor * (uTorchLight * tf * tf * 0.25);
    }

    // Altitude-based fog
    float altT = clamp((uCameraPos.y - uFogAltBase) / max(uFogAltRange, 0.0001), 0.0, 1.0);
    float fogStart = mix(uFogStartLow, uFogStartHigh, altT) * uFogStartMul;
    float fogRange = mix(uFogRangeLow, uFogRangeHigh, altT) * uFogRangeMul;
    float fog = clamp((viewDist - fogStart) / max(fogRange, 0.0001), 0.0, 1.0);
    rgb = mix(rgb, uFogColor, fog);

    // Refraction already composites the background: water owns the pixel
    FragColor = vec4(rgb, 1.0);
}
