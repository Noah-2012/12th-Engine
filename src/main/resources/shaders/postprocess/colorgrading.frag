#version 130
varying vec2 vTexCoord;

uniform sampler2D uColorTex;
uniform float uBrightness;
uniform float uContrast;
uniform float uSaturation;
uniform float uGamma;
uniform vec3  uShadows;
uniform vec3  uMidtones;
uniform vec3  uHighlights;

vec3 toLinear(vec3 c) { return pow(c, vec3(2.2)); }
vec3 toSRGB(vec3 c)   { return pow(c, vec3(1.0 / 2.2)); }

void main() {
    vec4 raw = texture2D(uColorTex, vTexCoord);
    vec3 c = toLinear(raw.rgb);

    // Brightness (additive lift)
    c += uBrightness;

    // Contrast (pivot at 0.5 in linear)
    c = (c - 0.5) * uContrast + 0.5;

    // Saturation
    float lum = dot(c, vec3(0.2126, 0.7152, 0.0722));
    c = mix(vec3(lum), c, uSaturation);

    // Shadow / midtone / highlight tint
    // Weight by luminance zone
    float l = clamp(lum, 0.0, 1.0);
    float shadowWeight    = clamp(1.0 - l * 3.0, 0.0, 1.0);
    float highlightWeight = clamp((l - 0.667) * 3.0, 0.0, 1.0);
    float midWeight       = 1.0 - shadowWeight - highlightWeight;

    c *= uShadows    * shadowWeight
       + uMidtones   * midWeight
       + uHighlights * highlightWeight;

    // Gamma
    c = pow(max(c, 0.0), vec3(1.0 / uGamma));

    c = clamp(toSRGB(c), 0.0, 1.0);
    gl_FragColor = vec4(c, raw.a);
}
