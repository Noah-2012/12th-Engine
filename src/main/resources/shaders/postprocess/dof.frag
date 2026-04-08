#version 130
varying vec2 vTexCoord;

uniform sampler2D uColorTex;
uniform sampler2D uDepthTex;
uniform float uFocalDistance;
uniform float uFocalRange;
uniform float uMaxBlur;
uniform float uNear;
uniform float uFar;
uniform int   uSamples;

float linearizeDepth(float d) {
    float z = d * 2.0 - 1.0;
    return (2.0 * uNear * uFar) / (uFar + uNear - z * (uFar - uNear));
}

// Golden-angle spiral for sample distribution (avoids banding)
const float GOLDEN_ANGLE = 2.399963;

void main() {
    vec2 texelSize = 1.0 / vec2(textureSize(uColorTex, 0));

    float rawDepth = texture2D(uDepthTex, vTexCoord).r;
    float depth    = linearizeDepth(rawDepth);

    // Circle of confusion: 0 at focal plane, 1 at max blur
    float coc = clamp(abs(depth - uFocalDistance) / uFocalRange, 0.0, 1.0);
    float blurRadius = coc * uMaxBlur;

    if (blurRadius < 0.5) {
        // In focus — early out
        gl_FragColor = texture2D(uColorTex, vTexCoord);
        return;
    }

    // Spiral disk sampling
    vec4 color = vec4(0.0);
    float totalWeight = 0.0;

    for (int i = 0; i < uSamples; i++) {
        float t = float(i) / float(uSamples);
        float angle = float(i) * GOLDEN_ANGLE;
        float r = sqrt(t) * blurRadius;
        vec2 offset = vec2(cos(angle), sin(angle)) * r * texelSize;
        vec2 sampleUV = vTexCoord + offset;

        if (sampleUV.x < 0.0 || sampleUV.x > 1.0 ||
            sampleUV.y < 0.0 || sampleUV.y > 1.0) continue;

        // Weight by sample's own CoC to avoid sharp edges bleeding into blur
        float sampleDepth = linearizeDepth(texture2D(uDepthTex, sampleUV).r);
        float sampleCoc   = clamp(abs(sampleDepth - uFocalDistance) / uFocalRange, 0.0, 1.0);
        float w = max(sampleCoc, coc);

        color += texture2D(uColorTex, sampleUV) * w;
        totalWeight += w;
    }

    gl_FragColor = color / max(totalWeight, 0.0001);
}
