#version 330 core

// Held-torch viewmodel: vertices are already in VIEW space (fixed to the
// camera, bottom-right like a Minecraft held item), so only the projection
// is applied. A tiny idle sway keeps it feeling hand-held.

layout(location = 0) in vec3 aPos;
layout(location = 1) in vec3 aColor;

uniform mat4  uProj;
uniform float uTime;

out vec3 vColor;

void main() {
    vec3 p = aPos;
    p.x += sin(uTime * 1.1) * 0.004;
    p.y += sin(uTime * 1.7 + 1.3) * 0.005;
    vColor = aColor;
    gl_Position = uProj * vec4(p, 1.0);
}
