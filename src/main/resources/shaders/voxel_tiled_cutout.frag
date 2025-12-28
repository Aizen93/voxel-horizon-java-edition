#version 330 core

in vec2 vTileMin;
in vec2 vUvLocal;
in vec3 vWorldPos;

uniform sampler2D uAtlas;
uniform vec2 uTileSize;
uniform float uAlphaCutoff = 0.5;

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

    // vegetation is double-sided: use abs to avoid “dark backface”
    vec3 n = normalize(cross(dFdx(vWorldPos), dFdy(vWorldPos)));
    tex.rgb *= mcShade(vec3(abs(n.x), abs(n.y), abs(n.z)));

    FragColor = tex;
}