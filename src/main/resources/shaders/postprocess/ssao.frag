#version 130
varying vec2 vTexCoord;

uniform sampler2D uColorTex;
uniform sampler2D uDepthTex;
uniform float uRadius;
uniform float uIntensity;
uniform float uBias;
uniform int   uSamples;
uniform int   uBlurRadius;
uniform float uNear;
uniform float uFar;

float linearizeDepth(float d) {
    float z = d * 2.0 - 1.0;
    return (2.0 * uNear * uFar) / (uFar + uNear - z * (uFar - uNear));
}

// Deterministic pseudo-random (no texture needed)
vec2 rand2(vec2 co) {
    float s = sin(dot(co, vec2(127.1, 311.7))) * 43758.5453123;
    float t = sin(dot(co, vec2(269.5, 183.3))) * 43758.5453123;
    return fract(vec2(s, t));
}

// --- Compute raw AO for a single pixel ---
float computeAO(vec2 uv) {
    float depth   = linearizeDepth(texture2D(uDepthTex, uv).r);
    float occlusion = 0.0;

    // Golden angle spiral hemisphere
    const float GOLDEN_ANGLE = 2.399963;
    vec2 texelSize = 1.0 / vec2(textureSize(uDepthTex, 0));

    for (int i = 0; i < uSamples; i++) {
        float t     = float(i) / float(uSamples);
        float angle = float(i) * GOLDEN_ANGLE + rand2(uv).x * 6.2831;
        float r     = sqrt(t) * uRadius;
        vec2 offset = vec2(cos(angle), sin(angle)) * r * texelSize;

        vec2 sampleUV = uv + offset;
        if (sampleUV.x < 0.0 || sampleUV.x > 1.0 ||
            sampleUV.y < 0.0 || sampleUV.y > 1.0) continue;

        float sampleDepth = linearizeDepth(texture2D(uDepthTex, sampleUV).r);

        // Range check + bias
        float rangeCheck = smoothstep(0.0, 1.0, uRadius / abs(depth - sampleDepth + 0.001));
        if (sampleDepth < depth - uBias) {
            occlusion += rangeCheck;
        }
    }

    return 1.0 - (occlusion / float(uSamples)) * uIntensity;
}

void main() {
    float rawDepth = texture2D(uDepthTex, vTexCoord).r;

    // Skip sky (max depth)
    if (rawDepth >= 0.9999) {
        gl_FragColor = texture2D(uColorTex, vTexCoord);
        return;
    }

    // --- Optional blur pass on AO (box blur, inline) ---
    float ao = 0.0;
    float count = 0.0;
    vec2 texelSize = 1.0 / vec2(textureSize(uDepthTex, 0));

    if (uBlurRadius <= 0) {
        ao = computeAO(vTexCoord);
    } else {
        for (int x = -uBlurRadius; x <= uBlurRadius; x++) {
            for (int y = -uBlurRadius; y <= uBlurRadius; y++) {
                vec2 offset = vec2(float(x), float(y)) * texelSize;
                ao += computeAO(vTexCoord + offset);
                count += 1.0;
            }
        }
        ao /= count;
    }

    ao = clamp(ao, 0.0, 1.0);

    vec4 scene  = texture2D(uColorTex, vTexCoord);
    gl_FragColor = vec4(scene.rgb * ao, scene.a);
}
