#version 330 core

in vec2 vUv;
uniform sampler2D uScene;
uniform vec2 uDir; // (texel, 0) or (0, texel)
out vec4 FragColor;

void main() {
    // 9-tap separable Gaussian
    float w0 = 0.227027;
    float w1 = 0.194594;
    float w2 = 0.121622;
    float w3 = 0.054054;
    float w4 = 0.016216;

    vec3 c = texture(uScene, vUv).rgb * w0;
    c += texture(uScene, vUv + uDir * 1.0).rgb * w1;
    c += texture(uScene, vUv - uDir * 1.0).rgb * w1;
    c += texture(uScene, vUv + uDir * 2.0).rgb * w2;
    c += texture(uScene, vUv - uDir * 2.0).rgb * w2;
    c += texture(uScene, vUv + uDir * 3.0).rgb * w3;
    c += texture(uScene, vUv - uDir * 3.0).rgb * w3;
    c += texture(uScene, vUv + uDir * 4.0).rgb * w4;
    c += texture(uScene, vUv - uDir * 4.0).rgb * w4;

    FragColor = vec4(c, 1.0);
}
