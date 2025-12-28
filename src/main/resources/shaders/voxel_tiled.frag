#version 330 core

in vec2 vTileMin;
in vec2 vUvLocal;
in vec3 vWorldPos;

uniform sampler2D uAtlas;
uniform vec2 uTileSize;

out vec4 FragColor;

float mcShade(vec3 n) {
    vec3 a = abs(n);

    // dominant axis
    if (a.y >= a.x && a.y >= a.z) {
        return (n.y > 0.0) ? 1.00 : 0.55; // top / bottom
    }
    if (a.x >= a.z) return 0.80;          // X sides
    return 0.70;                           // Z sides
}

void main() {
    vec2 uv = vTileMin + fract(vUvLocal) * uTileSize;
    vec4 tex = texture(uAtlas, uv);

    vec3 n = normalize(cross(dFdx(vWorldPos), dFdy(vWorldPos)));
    tex.rgb *= mcShade(n);

    FragColor = tex;
}