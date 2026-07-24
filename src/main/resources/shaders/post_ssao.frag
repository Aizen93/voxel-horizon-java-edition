#version 330 core

// Screen-space ambient occlusion from the opaque depth buffer.
// View-space position is reconstructed from depth; the normal comes from
// screen-space derivatives, so no G-buffer is needed. 12 spiral taps with a
// per-pixel hash rotation; a half-res Gaussian blur smooths the result.

in vec2 vUv;

uniform sampler2D uDepth;
uniform mat4  uInvProj;
uniform float uRadius;     // world-ish units at z=1
uniform float uStrength;

out vec4 FragColor;

vec3 viewPos(vec2 uv) {
    float d = texture(uDepth, uv).r;
    vec4 ndc = vec4(uv * 2.0 - 1.0, d * 2.0 - 1.0, 1.0);
    vec4 v = uInvProj * ndc;
    return v.xyz / v.w;
}

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

void main() {
    float d0 = texture(uDepth, vUv).r;
    if (d0 >= 0.9999) { // sky
        FragColor = vec4(1.0);
        return;
    }

    vec3 p = viewPos(vUv);
    vec3 n = normalize(cross(dFdx(p), dFdy(p)));

    float ang0 = hash(gl_FragCoord.xy) * 6.2831853;
    float radius = uRadius;
    // Screen-space radius shrinks with distance (p.z is negative view depth)
    float rUv = radius / max(-p.z, 0.5);

    float occ = 0.0;
    const int TAPS = 12;
    for (int i = 0; i < TAPS; i++) {
        float t = (float(i) + 0.5) / float(TAPS);
        float ang = ang0 + t * 6.2831853 * 2.0;
        vec2 offs = vec2(cos(ang), sin(ang)) * t * rUv * 0.08;
        vec3 q = viewPos(vUv + offs);
        vec3 dv = q - p;
        float dist = length(dv);
        // Occlusion from samples in front of the surface, range-limited
        float align = max(dot(n, dv / max(dist, 1e-4)) - 0.08, 0.0);
        occ += align * smoothstep(radius, radius * 0.25, dist);
    }
    occ = occ / float(TAPS);

    float ao = clamp(1.0 - occ * uStrength * 2.2, 0.0, 1.0);
    FragColor = vec4(ao, ao, ao, 1.0);
}
