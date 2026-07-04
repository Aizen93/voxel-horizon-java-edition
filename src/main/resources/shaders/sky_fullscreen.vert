#version 330 core

// Fullscreen triangle from gl_VertexID; passes NDC coords so the fragment
// shader can reconstruct a per-pixel world-space view ray.
out vec2 vNdc;

void main() {
    vec2 p = vec2(
        (gl_VertexID == 1) ? 3.0 : -1.0,
        (gl_VertexID == 2) ? 3.0 : -1.0
    );
    vNdc = p;
    gl_Position = vec4(p, 0.9999, 1.0);
}
