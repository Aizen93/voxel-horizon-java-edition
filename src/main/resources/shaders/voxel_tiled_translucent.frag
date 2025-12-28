#version 330 core

in vec2 vTileMin;
in vec2 vUvLocal;

uniform sampler2D uAtlas;
uniform vec2 uTileSize;
uniform float uTime;

out vec4 FragColor;

void main() {
    // Local UV inside the tile (0..1)
    vec2 local = fract(vUvLocal);

    // FLOW: scroll inside the tile (wraps automatically via fract)
    float speed = 0.20; // increase for faster flow
    vec2 flowDir = normalize(vec2(1.0, 0.35));
    local = fract(local + flowDir * uTime * speed);

    // Add subtle wave distortion (still in local space)
    float waveAmp  = 0.06;   // 0.02..0.10 (local units)
    float waveFreq = 10.0;
    local.x += sin(uTime * 2.0 + local.y * waveFreq) * waveAmp;
    local.y += cos(uTime * 2.2 + local.x * waveFreq) * waveAmp;

    // Wrap again so we never leave [0..1)
    local = fract(local);

    // Rebuild atlas UV (guaranteed to remain inside this tile)
    vec2 uv = vTileMin + local * uTileSize;

    vec4 tex = texture(uAtlas, uv);


    vec3 rgb = tex.rgb;
    float baseAlpha = 0.65;
    float a = clamp(tex.a * baseAlpha, 0.0, 0.70);

    FragColor = vec4(rgb, a);
}
