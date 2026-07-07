#version 330 core

// Volumetric sun shafts: march from the camera toward each pixel's scene
// depth, sampling the cascaded shadow maps at every step. Lit air scatters
// sunlight toward the camera (strong forward phase), shadowed air doesn't —
// so crepuscular rays appear behind trees, ridges and cave mouths for real,
// not as a screen-space radial smear.
//
// NOTE: cascade sampling stays inlined per map — passing a sampler2DShadow
// as a function parameter silently breaks hardware compare on some drivers.

in vec2 vUv;

uniform sampler2D uDepth;
uniform sampler2DShadow uShadow0;
uniform sampler2DShadow uShadow1;
uniform sampler2DShadow uShadow2;

uniform mat4  uInvViewProj;
uniform mat4  uLightMVP0;
uniform mat4  uLightMVP1;
uniform mat4  uLightMVP2;
uniform vec3  uCascadeSplits;
uniform vec3  uCascadeBias;
uniform vec3  uCameraPos;
uniform vec3  uSunDir;
uniform vec3  uColor;    // shaft tint, already day/night/storm scaled
uniform float uStrength; // 0 disables (sun down, underwater)

out vec4 FragColor;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

void main() {
    if (uStrength < 0.001) {
        FragColor = vec4(0.0);
        return;
    }

    float d = texture(uDepth, vUv).r;
    vec4 ndc = vec4(vUv * 2.0 - 1.0, d * 2.0 - 1.0, 1.0);
    vec4 wp4 = uInvViewProj * ndc;
    vec3 wp = wp4.xyz / wp4.w;

    vec3 ray = wp - uCameraPos;
    float dist = length(ray);
    vec3 dir = ray / max(dist, 1e-4);

    // Forward-scattering phase: shafts bloom when looking toward the sun;
    // tiny isotropic floor so lit air barely glows off-axis (no milky veil)
    float phase = 0.035 + 0.965 * pow(max(dot(dir, normalize(uSunDir)), 0.0), 10.0);

    float maxD = min(dist, 130.0);
    const int STEPS = 24;
    float stepLen = maxD / float(STEPS);
    float t = stepLen * hash(gl_FragCoord.xy); // dithered start hides banding

    float acc = 0.0;
    for (int i = 0; i < STEPS; i++) {
        vec3 p = uCameraPos + dir * t;
        float dxz = length(p.xz - uCameraPos.xz);

        float lit;
        if (dxz < uCascadeSplits.x) {
            vec4 ls = uLightMVP0 * vec4(p, 1.0);
            vec3 q = ls.xyz / ls.w * 0.5 + 0.5;
            lit = (q.z >= 1.0) ? 1.0 : texture(uShadow0, vec3(q.xy, q.z - uCascadeBias.x));
        } else if (dxz < uCascadeSplits.y) {
            vec4 ls = uLightMVP1 * vec4(p, 1.0);
            vec3 q = ls.xyz / ls.w * 0.5 + 0.5;
            lit = (q.z >= 1.0) ? 1.0 : texture(uShadow1, vec3(q.xy, q.z - uCascadeBias.y));
        } else {
            vec4 ls = uLightMVP2 * vec4(p, 1.0);
            vec3 q = ls.xyz / ls.w * 0.5 + 0.5;
            lit = (q.z >= 1.0) ? 1.0 : texture(uShadow2, vec3(q.xy, q.z - uCascadeBias.z));
        }

        acc += lit;
        t += stepLen;
    }

    // Average lit fraction x how much participating air the ray crossed
    float shaft = (acc / float(STEPS)) * phase * (1.0 - exp(-maxD * 0.012));
    FragColor = vec4(uColor * (shaft * uStrength), 1.0);
}
