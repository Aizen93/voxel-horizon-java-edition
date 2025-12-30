#version 330 core

in vec2 vTileMin;
in vec2 vUvLocal;
in vec3 vWorldPos;

uniform sampler2D uAtlas;
uniform vec2 uTileSize;
uniform float uAlphaCutoff = 0.5;

// Fog controls (set from Java)
uniform vec3  uCameraPos;
uniform vec3  uFogColor;

uniform float uFogAltBase;
uniform float uFogAltRange;
uniform float uFogStartLow;
uniform float uFogStartHigh;
uniform float uFogRangeLow;
uniform float uFogRangeHigh;
uniform float uFogStartMul;   // 1.0 normally, <1 tightens fog (rain/mist)
uniform float uFogRangeMul;   // 1.0 normally, <1 tightens fog (rain/mist)

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

    if (tex.a < uAlphaCutoff) discard;

    vec3 n = normalize(cross(dFdx(vWorldPos), dFdy(vWorldPos)));
    vec3 color = tex.rgb * mcShade(vec3(abs(n.x), abs(n.y), abs(n.z)));

    float dist = length(vWorldPos - uCameraPos);
    float camAlt = uCameraPos.y;

    float altT = clamp((camAlt - uFogAltBase) / max(uFogAltRange, 0.0001), 0.0, 1.0);
    float fogStart = mix(uFogStartLow, uFogStartHigh, altT) * uFogStartMul;
    float fogRange = mix(uFogRangeLow, uFogRangeHigh, altT) * uFogRangeMul;
    float fog = clamp((dist - fogStart) / max(fogRange, 0.0001), 0.0, 1.0);

    color = mix(color, uFogColor, fog);

    FragColor = vec4(color, 1.0);
}