#version 330 core
out vec2 vUv;

// Fullscreen triangle using gl_VertexID (no VBO needed)
void main() {
    vec2 pos;
    if (gl_VertexID == 0) pos = vec2(-1.0, -1.0);
    else if (gl_VertexID == 1) pos = vec2( 3.0, -1.0);
    else pos = vec2(-1.0,  3.0);

    vUv = pos * 0.5 + 0.5; // maps to [0..1] (some values >1, that’s fine)
    gl_Position = vec4(pos, 0.0, 1.0);
}