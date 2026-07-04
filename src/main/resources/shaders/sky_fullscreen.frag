#version 330 core

// Directional sky: per-pixel view ray -> gradient + sun disc + procedural
// animated cloud layer. The sun position matches the shadow/light direction
// from FogCycle, so sunrise/sunset visuals line up with the lighting.

in vec2 vNdc;

uniform mat4  uInvProj;
uniform mat4  uInvViewRot;
uniform vec3  uCameraPos;
uniform vec3  uSunDir;
uniform vec3  uSkyTopColor;
uniform vec3  uSkyHorizonColor;
uniform float uTwilight01;
uniform float uTime;
uniform float uCloudCover;   // 0 = clear sky, 1 = overcast
uniform float uCloudHeight;  // render-space Y of the cloud layer

uniform float uUnderwater;
uniform vec3  uUnderwaterColor;

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

void main() {
    // Per-pixel world-space view direction
    vec4 vp = uInvProj * vec4(vNdc, -1.0, 1.0);
    vec3 dir = normalize(mat3(uInvViewRot) * (vp.xyz / vp.w));
    vec3 sun = normalize(uSunDir);

    // ---- Underwater: the "sky" is the water volume itself ----
    if (uUnderwater > 0.5) {
        float upw = clamp(dir.y * 0.5 + 0.5, 0.0, 1.0);
        vec3 uw = mix(uUnderwaterColor * 0.25, uUnderwaterColor * 2.3, pow(upw, 2.0));
        // Filtered sun glow bleeding down through the surface
        float sd = max(dot(dir, sun), 0.0);
        uw += vec3(0.30, 0.50, 0.55) * pow(sd, 20.0) * smoothstep(0.0, 0.2, sun.y);
        FragColor = vec4(uw, 1.0);
        return;
    }

    // ---- Sky gradient ----
    float up = dir.y;
    float t = pow(clamp(up, 0.0, 1.0), 0.55);
    vec3 col = mix(uSkyHorizonColor, uSkyTopColor, t);
    // Below the horizon: darkened haze (terrain covers most of it anyway)
    if (up < 0.0) {
        col = mix(uSkyHorizonColor, uSkyHorizonColor * 0.5, clamp(-up * 5.0, 0.0, 1.0));
    }

    // ---- Twilight warm band, strongest toward the sun ----
    float horizonGlow = exp(-abs(up) * 6.0);
    float sunward = pow(clamp(dot(normalize(dir.xz + 1e-5), normalize(sun.xz + 1e-5)) * 0.5 + 0.5, 0.0, 1.0), 3.0);
    vec3 warm = vec3(1.0, 0.52, 0.20);
    col = mix(col, warm, uTwilight01 * horizonGlow * sunward * 0.85);

    // ---- Sun disc + glow ----
    float d = dot(dir, sun);
    float sunUp = smoothstep(-0.08, 0.05, sun.y);
    vec3 sunColor = mix(vec3(1.0, 0.45, 0.15), vec3(1.0, 0.97, 0.88), smoothstep(0.0, 0.35, sun.y));
    float disc = smoothstep(0.99955, 0.99985, d);
    float glow = pow(max(d, 0.0), 550.0) * 0.9 + pow(max(d, 0.0), 24.0) * 0.09;
    col += sunColor * (disc * 1.8 + glow) * sunUp;

    // ---- Night: moon (opposite the sun) + twinkling stars ----
    float night01 = smoothstep(0.02, -0.12, sun.y);
    if (night01 > 0.001) {
        vec3 moon = -sun;
        float md = dot(dir, moon);
        float mdisc = smoothstep(0.99965, 0.99988, md);
        float mglow = pow(max(md, 0.0), 900.0) * 0.45 + pow(max(md, 0.0), 40.0) * 0.03;
        col += vec3(0.88, 0.92, 1.0) * (mdisc * 0.85 + mglow) * night01;

        // Star field: one star in ~1/5 of the cells of a direction-space grid
        vec2 sp = vec2(atan(dir.x, dir.z), asin(clamp(dir.y, -1.0, 1.0))) * 48.0;
        vec2 cell = floor(sp);
        float h = hash21(cell);
        if (h > 0.80) {
            vec2 starPos = (vec2(hash21(cell + 7.31), hash21(cell + 3.17)) - 0.5) * 0.7;
            float star = smoothstep(0.10, 0.02, length(fract(sp) - 0.5 - starPos));
            float twinkle = 0.65 + 0.35 * sin(uTime * (1.2 + h * 2.5) + h * 41.0);
            col += vec3(0.75, 0.82, 1.0) * star * twinkle * night01
                    * smoothstep(0.0, 0.12, dir.y);
        }
    }

    // ---- Volumetric clouds: ray-march an 90-block-thick slab ----
    // 8 density steps + 1 sun tap per step gives clouds real silhouettes,
    // soft bellies and bright rims — only sky pixels pay for it.
    if (uCloudCover > 0.01) {
        float slabBottom = uCloudHeight;
        float slabTop = uCloudHeight + 90.0;

        float t0;
        float t1;
        if (abs(dir.y) < 0.004) {
            t0 = -1.0;
            t1 = -1.0;
        } else {
            float ta = (slabBottom - uCameraPos.y) / dir.y;
            float tb = (slabTop - uCameraPos.y) / dir.y;
            t0 = max(min(ta, tb), 0.0);
            t1 = min(max(ta, tb), 26000.0);
        }

        if (t1 > t0) {
            float threshold = mix(0.80, 0.44, uCloudCover);
            vec2 wind = vec2(uTime * 0.009, uTime * 0.0035);

            float stepLen = (t1 - t0) / 8.0;
            float transmittance = 1.0;
            vec3 acc = vec3(0.0);

            // Moonlit-dim at night so clouds don't glow against the dark sky
            float cloudNightDim = mix(0.30, 1.0, smoothstep(-0.12, 0.05, sun.y));
            vec3 cloudDark = vec3(0.50, 0.54, 0.63) * clamp(uSkyTopColor * 1.6 + 0.12, 0.0, 1.25) * cloudNightDim;
            vec3 cloudLit = vec3(1.05, 1.02, 0.98) * clamp(uSkyTopColor * 1.6 + 0.14, 0.0, 1.3) * cloudNightDim;
            cloudLit = mix(cloudLit, warm * 1.15, uTwilight01 * 0.5);

            for (int i = 0; i < 8; i++) {
                float t = t0 + stepLen * (float(i) + 0.5);
                vec3 p = uCameraPos + dir * t;

                // Vertical profile: dense in the middle of the slab
                float hFrac = clamp((p.y - slabBottom) / (slabTop - slabBottom), 0.0, 1.0);
                float profile = hFrac * (1.0 - hFrac) * 4.0;

                float f = fbm(p.xz * 0.0016 + wind);
                float dens = smoothstep(threshold, threshold + 0.24, f) * profile;
                if (dens <= 0.001) continue;

                // One tap toward the sun for self-shadowing
                vec3 ps = p + sun * 55.0;
                float fs = fbm(ps.xz * 0.0016 + wind);
                float densS = smoothstep(threshold, threshold + 0.24, fs)
                        * clamp((ps.y - slabBottom) / (slabTop - slabBottom), 0.0, 1.0);
                float lit = exp(-densS * 2.2);

                vec3 cCol = mix(cloudDark, cloudLit, lit);

                float a = 1.0 - exp(-dens * stepLen * 0.045);
                acc += cCol * a * transmittance;
                transmittance *= 1.0 - a;
                if (transmittance < 0.03) break;
            }

            // Distance fade into the horizon haze
            float fadeD = exp(-t0 * 0.00030);
            float cloudAmount = (1.0 - transmittance) * fadeD;
            vec3 cloudColor = acc / max(1.0 - transmittance, 1e-4);
            col = mix(col, cloudColor, clamp(cloudAmount, 0.0, 0.95));
        }
    }

    FragColor = vec4(col, 1.0);
}
