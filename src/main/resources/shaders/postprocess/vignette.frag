#version 130
varying vec2 vTexCoord;

uniform sampler2D uColorTex;
uniform float uRadius;
uniform float uSoftness;
uniform float uStrength;
uniform vec3  uColor;

void main() {
    vec4 scene = texture2D(uColorTex, vTexCoord);

    // Distance from center, aspect-corrected
    vec2 uv = vTexCoord - vec2(0.5);
    float dist = length(uv);

    // Smooth step: 0 in clear center, 1 at darkened edges
    float vignette = smoothstep(uRadius, uRadius - uSoftness, dist);

    // vignette == 1 means center (no darkening), 0 means edge (full darkening)
    float darkening = (1.0 - vignette) * uStrength;
    vec3 result = mix(scene.rgb, uColor, darkening);

    gl_FragColor = vec4(result, scene.a);
}
