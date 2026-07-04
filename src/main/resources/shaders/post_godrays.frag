#version 330 core

// Screen-space light shafts: radial march from each pixel toward the sun,
// accumulating sky brightness (occluded pixels contribute nothing).

in vec2 vUv;
uniform sampler2D uScene;
uniform sampler2D uDepth;
uniform vec2  uSunUv;
uniform float uVisible; // 0 = sun off screen / below horizon
uniform float uAspect;  // width / height
out vec4 FragColor;

const int SAMPLES = 48;

// Contributions must fade smoothly to zero at the screen border. A hard
// cutoff (break / clamp-to-edge) stamps scaled copies of the screen
// rectangle into the shafts, which shows up as nested white rectangles.
float borderFade(vec2 uv) {
    vec2 b = smoothstep(vec2(-0.02), vec2(0.10), uv)
           * (1.0 - smoothstep(vec2(0.90), vec2(1.02), uv));
    return b.x * b.y;
}

void main() {
    if (uVisible <= 0.001) {
        FragColor = vec4(0.0);
        return;
    }

    // Shafts live around the sun: without this radial falloff every bright
    // sky pixel acts as a light source and the whole screen washes out.
    float sunDist = length((vUv - uSunUv) * vec2(uAspect, 1.0));
    float radial = exp(-sunDist * 2.4);

    vec2 delta = (uSunUv - vUv) / float(SAMPLES);
    vec2 uv = vUv;

    float decay = 0.955;
    float weight = 1.0;
    vec3 acc = vec3(0.0);

    for (int i = 0; i < SAMPLES; i++) {
        uv += delta;
        float border = borderFade(uv);
        if (border > 0.0) {
            // Only unoccluded sky feeds the shafts
            float d = texture(uDepth, uv).r;
            if (d > 0.99995) {
                vec3 c = texture(uScene, uv).rgb;
                float luma = dot(c, vec3(0.2126, 0.7152, 0.0722));
                acc += c * (luma * weight * border);
            }
        }
        weight *= decay;
    }

    acc /= float(SAMPLES);
    FragColor = vec4(acc * radial * uVisible, 1.0);
}
