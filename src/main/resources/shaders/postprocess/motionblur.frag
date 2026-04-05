#version 130
varying vec2 vTexCoord;

uniform sampler2D uColorTex;
uniform sampler2D uDepthTex;
uniform mat4  uInvVP;
uniform mat4  uPrevVP;
uniform float uBlurStrength;
uniform int   uSamples;
uniform float uNear;
uniform float uFar;
uniform int   uDebugMode; // 0=normal, 1=velocity, 2=depth, 3=worldpos

void main() {
    vec4 originalColor = texture2D(uColorTex, vTexCoord);

    float depthSample = texture2D(uDepthTex, vTexCoord).r;

    if (depthSample >= 0.9999) {
        gl_FragColor = originalColor;
        return;
    }

    float z = depthSample * 2.0 - 1.0;
    vec4 clipPos = vec4(vTexCoord * 2.0 - 1.0, z, 1.0);

    vec4 worldPos = uInvVP * clipPos;
    if (abs(worldPos.w) < 0.001) {
        gl_FragColor = originalColor;
        return;
    }
    worldPos /= worldPos.w;


    vec4 prevClip = uPrevVP * worldPos;
    if (abs(prevClip.w) < 0.001) {
        gl_FragColor = originalColor;
        return;
    }
    prevClip /= prevClip.w;

    // Clamp RAW velocity BEFORE applying strength
    vec2 rawVelocity = (prevClip.xy - clipPos.xy) * 0.5;
    float rawLen = length(rawVelocity);
    if (rawLen < 0.00001) {
        gl_FragColor = originalColor;
        return;
    }
    // Clamp raw reprojection to max 2% of screen
    if (rawLen > 0.02) {
        rawVelocity = normalize(rawVelocity) * 0.02;
    }

    // NOW apply strength — this only affects blur length, not reprojection accuracy
    vec2 velocity = rawVelocity * uBlurStrength;

    // Clamp final velocity so no single step ever leaves screen
    float velLen = length(velocity);
    float maxFinalVel = 0.5; // allow up to 50% of screen total blur length
    if (velLen > maxFinalVel) {
        velocity = normalize(velocity) * maxFinalVel;
    }

    // Step size per sample
    vec2 step = velocity / float(uSamples);

    // Accumulate — skip samples that go out of bounds instead of breaking
    vec4 color = originalColor;
    vec2 uv = vTexCoord;
    int count = 1;

    for (int i = 1; i < uSamples; i++) {
        uv += step;
        // Skip out-of-bounds samples but CONTINUE — don't break
        // This prevents the count from being too low on one side
        if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) {
            continue;
        }
        color += texture2D(uColorTex, uv);
        count++;
    }

    gl_FragColor = color / float(count);
}