#version 130
varying vec2 vTexCoord;

uniform sampler2D uColorTex;
uniform float uStrength;
uniform float uFalloff;

void main() {
    // Vector from screen center
    vec2 dir = vTexCoord - vec2(0.5);
    float dist = length(dir);

    // Falloff: lerp between full-screen and edge-only aberration
    float factor = mix(1.0, dist * 2.0, uFalloff) * uStrength;

    vec2 offset = normalize(dir + vec2(0.0001)) * factor;

    float r = texture2D(uColorTex, vTexCoord + offset).r;
    float g = texture2D(uColorTex, vTexCoord        ).g;
    float b = texture2D(uColorTex, vTexCoord - offset).b;
    float a = texture2D(uColorTex, vTexCoord).a;

    gl_FragColor = vec4(r, g, b, a);
}
