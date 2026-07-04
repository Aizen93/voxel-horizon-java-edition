#version 330 core

// Temporal antialiasing: the projection matrix is jittered by a subpixel
// Halton offset every frame, and this pass blends the current frame with the
// reprojected history. A 3x3 neighborhood clamp on the history rejects stale
// colors (moving water, clouds, dissolve dither) without ghosting.

in vec2 vUv;

uniform sampler2D uScene;    // current frame (HDR resolve)
uniform sampler2D uHistory;  // accumulated previous output
uniform sampler2D uDepth;    // current opaque depth
uniform mat4  uInvViewProj;  // current frame, unjittered
uniform mat4  uPrevViewProj; // previous frame, unjittered
uniform vec2  uTexel;        // 1 / resolution
uniform float uBlend;        // share of the current frame (1.0 = no history)

out vec4 FragColor;

void main() {
    vec3 current = texture(uScene, vUv).rgb;

    if (uBlend >= 0.999) {
        FragColor = vec4(current, 1.0);
        return;
    }

    // Reconstruct the world position of this pixel and find where it was on
    // screen last frame (static world: camera motion only).
    float d = texture(uDepth, vUv).r;
    vec4 world = uInvViewProj * vec4(vUv * 2.0 - 1.0, d * 2.0 - 1.0, 1.0);
    world /= world.w;

    vec4 prevClip = uPrevViewProj * world;
    if (prevClip.w <= 0.0) {
        FragColor = vec4(current, 1.0);
        return;
    }
    vec2 prevUv = prevClip.xy / prevClip.w * 0.5 + 0.5;
    if (prevUv.x < 0.0 || prevUv.x > 1.0 || prevUv.y < 0.0 || prevUv.y > 1.0) {
        FragColor = vec4(current, 1.0);
        return;
    }

    vec3 history = texture(uHistory, prevUv).rgb;

    // Clamp history to the current 3x3 neighborhood: anything outside moved
    // or changed (animation, disocclusion) and must not ghost.
    vec3 mn = current;
    vec3 mx = current;
    for (int dy = -1; dy <= 1; dy++) {
        for (int dx = -1; dx <= 1; dx++) {
            if (dx == 0 && dy == 0) continue;
            vec3 c = texture(uScene, vUv + vec2(dx, dy) * uTexel).rgb;
            mn = min(mn, c);
            mx = max(mx, c);
        }
    }
    history = clamp(history, mn, mx);

    FragColor = vec4(mix(history, current, uBlend), 1.0);
}
