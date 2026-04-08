#version 130
varying vec2 vTexCoord;

uniform sampler2D uColorTex;
uniform float uThreshold;
uniform float uIntensity;
uniform int uBlurRadius;

// Gaussian weights for up to radius 8
float gaussWeight(float offset, float sigma) {
    return exp(-(offset * offset) / (2.0 * sigma * sigma));
}

void main() {
    vec2 texelSize = 1.0 / vec2(textureSize(uColorTex, 0));
    vec4 baseColor = texture2D(uColorTex, vTexCoord);

    // --- Brightness extraction pass (inline, single-pass approx) ---
    // We accumulate a blurred bright-only image and add it back.
    vec4 bloom = vec4(0.0);
    float totalWeight = 0.0;
    float sigma = float(uBlurRadius) * 0.5;

    for (int x = -uBlurRadius; x <= uBlurRadius; x++) {
        for (int y = -uBlurRadius; y <= uBlurRadius; y++) {
            vec2 offset = vec2(float(x), float(y)) * texelSize;
            vec4 sample = texture2D(uColorTex, vTexCoord + offset);

            // Extract only bright pixels
            float brightness = dot(sample.rgb, vec3(0.2126, 0.7152, 0.0722));
            float contribution = max(brightness - uThreshold, 0.0);
            vec4 brightSample = sample * (contribution / max(brightness, 0.0001));

            float w = gaussWeight(float(x), sigma) * gaussWeight(float(y), sigma);
            bloom += brightSample * w;
            totalWeight += w;
        }
    }

    bloom /= totalWeight;
    gl_FragColor = baseColor + bloom * uIntensity;
}
