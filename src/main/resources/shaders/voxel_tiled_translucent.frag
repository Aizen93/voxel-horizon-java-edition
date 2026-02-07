#version 330 core

in vec2 vTileMin;
in vec2 vUvLocal;
in vec3 vWorldPos;

uniform sampler2D uAtlas;
uniform vec2 uTileSize;
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

out vec4 FragColor;

// Compute animated water normal from layered sine waves
vec3 computeWaterNormal(vec3 worldPos, float time) {
    vec2 pos = worldPos.xz;
    vec2 gradient = vec2(0.0);

    // 4 wave layers with different frequencies and directions
    const vec2[4] dirs = vec2[4](
        normalize(vec2(1.0, 0.6)),
        normalize(vec2(-0.7, 1.0)),
        normalize(vec2(0.5, -0.8)),
        normalize(vec2(-1.0, -0.3))
    );
    const float[4] freqs = float[4](0.15, 0.35, 0.8, 1.5);
    const float[4] amps = float[4](0.08, 0.05, 0.025, 0.015);
    const float[4] speeds = float[4](0.5, 0.8, 1.2, 1.5);

    for (int i = 0; i < 4; i++) {
        float phase = dot(pos, dirs[i]) * freqs[i] + time * speeds[i];
        gradient += dirs[i] * cos(phase) * freqs[i] * amps[i];
    }

    return normalize(vec3(-gradient.x, 1.0, -gradient.y));
}

// Fresnel approximation (Schlick)
float fresnel(float cosTheta, float F0) {
    return F0 + (1.0 - F0) * pow(1.0 - cosTheta, 5.0);
}

void main() {
    // Texture animation: flow + distortion
    vec2 local = fract(vUvLocal + normalize(vec2(1.0, 0.35)) * uTime * 0.12);
    local.x += sin(uTime * 1.5 + local.y * 8.0) * 0.04;
    local.y += cos(uTime * 1.6 + local.x * 8.0) * 0.04;
    
    vec4 tex = texture(uAtlas, vTileMin + fract(local) * uTileSize);
    vec3 waterColor = tex.rgb * vec3(0.3, 0.5, 0.7);

    // View direction and animated water normal
    vec3 viewDir = normalize(uCameraPos - vWorldPos);
    vec3 normal = computeWaterNormal(vWorldPos, uTime);
    float NdotV = max(dot(normal, viewDir), 0.0);

    // Fresnel effect
    float fresnelFactor = fresnel(NdotV, 0.02);

    // Sky reflection
    vec3 reflectDir = reflect(-viewDir, normal);
    float skyT = pow(clamp(reflectDir.y * 0.5 + 0.5, 0.0, 1.0), 0.6);
    vec3 skyReflection = mix(uSkyHorizonColor, uSkyTopColor, skyT);
    skyReflection = mix(skyReflection, uSkyHorizonColor * 1.1, exp(-abs(reflectDir.y) * 3.0) * 0.3);

    // Sun specular highlight
    vec3 halfVec = normalize(normalize(uSunDir) + viewDir);
    float NdotH = max(dot(normal, halfVec), 0.0);
    vec3 sunSpecular = vec3(1.0, 0.98, 0.9) * (pow(NdotH, 256.0) * 0.8 + pow(NdotH, 32.0) * 0.15);
    sunSpecular *= smoothstep(-0.1, 0.2, uSunDir.y);

    // Combine water color + sky reflection + sun specular
    vec3 rgb = mix(waterColor, skyReflection, 0.3 + fresnelFactor * 0.35) + sunSpecular;

    // Altitude-based fog
    float dist = length(vWorldPos - uCameraPos);
    float altT = clamp((uCameraPos.y - uFogAltBase) / max(uFogAltRange, 0.0001), 0.0, 1.0);
    float fogStart = mix(uFogStartLow, uFogStartHigh, altT) * uFogStartMul;
    float fogRange = mix(uFogRangeLow, uFogRangeHigh, altT) * uFogRangeMul;
    float fog = clamp((dist - fogStart) / max(fogRange, 0.0001), 0.0, 1.0);
    rgb = mix(rgb, uFogColor, fog);

    // Fresnel-based alpha
    float alpha = clamp(tex.a * mix(0.6, 0.85, fresnelFactor), 0.0, 0.88);

    FragColor = vec4(rgb, alpha);
}