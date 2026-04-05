#version 130
varying vec2 vTexCoord;

uniform sampler2D uColorTex;
uniform sampler2D uDepthTex;
uniform float uNear;
uniform float uFar;
uniform float uFogDensity;
uniform vec3  uFogColor;

float linearizeDepth(float d) {
    float z = d * 2.0 - 1.0;
    return (2.0 * uNear * uFar) / (uFar + uNear - z * (uFar - uNear));
}

void main() {
    vec4  scene    = texture2D(uColorTex, vTexCoord);
    float rawDepth = texture2D(uDepthTex, vTexCoord).r;
    float depth    = linearizeDepth(rawDepth);
    float fogFactor = exp(-pow(uFogDensity * depth, 2.0));
    fogFactor = clamp(fogFactor, 0.0, 1.0);
    gl_FragColor = mix(vec4(uFogColor, 1.0), scene, fogFactor);
}