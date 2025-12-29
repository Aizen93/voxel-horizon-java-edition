#version 330 core

in vec2 vTileMin;
in vec2 vUvLocal;
in vec3 vWorldPos;

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

out vec4 FragColor;

float mcShade(vec3 n) {
    vec3 a = abs(n);
    if (a.y >= a.x && a.y >= a.z) return (n.y > 0.0) ? 1.00 : 0.55;
    if (a.x >= a.z) return 0.80;
    return 0.70;
}

void main() {
    vec2 uv = vTileMin + fract(vUvLocal) * uTileSize;
    vec4 tex = texture(uAtlas, uv);

    vec3 n = normalize(cross(dFdx(vWorldPos), dFdy(vWorldPos)));
    vec3 color = tex.rgb * mcShade(n);

    float dist = length(vWorldPos - uCameraPos);
    float camAlt = uCameraPos.y;

    float altT = clamp((camAlt - uFogAltBase) / max(uFogAltRange, 0.0001), 0.0, 1.0);
    float fogStart = mix(uFogStartLow, uFogStartHigh, altT);
    float fogRange = mix(uFogRangeLow, uFogRangeHigh, altT);
    float fog = clamp((dist - fogStart) / max(fogRange, 0.0001), 0.0, 1.0);

    color = mix(color, uFogColor, fog);

    FragColor = vec4(color, tex.a);
}