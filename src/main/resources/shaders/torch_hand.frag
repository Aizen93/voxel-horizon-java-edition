#version 330 core

// Held-torch viewmodel. Flame vertices are HDR-tagged (color > 1): they
// scale with the flicker uniform, so toggling the light visibly dims and
// re-lights the flame while the wooden stick stays constant.

in vec3 vColor;

uniform float uFlicker;

out vec4 FragColor;

void main() {
    vec3 c = vColor;
    if (c.r > 1.0) c *= uFlicker;
    FragColor = vec4(c, 1.0);
}
