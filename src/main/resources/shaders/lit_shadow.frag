#version 330 core
in vec3 vNormalWorld;
in vec3 vFragWorld;
in vec4 vFragLightSpace;
in vec2 vUv;

uniform vec3 uLightDirWorld;
uniform vec3 uLightColor;
uniform vec3 uAmbient;
uniform sampler2D uDiffuseTex;
uniform sampler2DShadow uShadowMap;
uniform int uUseTexture;
uniform vec3 uBaseColor;

out vec4 fragColor;

// 16 pseudo-randomly distributed points inside a circle
const vec2 poissonDisk[16] = vec2[](
        vec2(-0.94201624, -0.39906216), vec2(0.94558609, -0.76890725),
        vec2(-0.094184101, -0.92938870), vec2(0.34495938, 0.29387760),
        vec2(-0.91588581, 0.45771432), vec2(-0.81544232, -0.87912464),
        vec2(-0.38277543, 0.27676845), vec2(0.97484398, 0.75648379),
        vec2(0.44323325, -0.97511554), vec2(0.53742981, -0.47373420),
        vec2(-0.26496911, -0.41893023), vec2(0.79197514, 0.19090188),
        vec2(-0.24188840, 0.99706507), vec2(-0.81409955, 0.91437590),
        vec2(0.19984126, 0.78641367), vec2(0.14383161, -0.14100790)
    );

// Generates a random angle based on screen coordinates
float randomAngle(vec2 seed) {
    return fract(sin(dot(seed, vec2(12.9898, 78.233))) * 43758.5453) * 6.283285;
}

float shadowFactor(vec4 fragLightSpace, vec3 normalWorld, vec3 lightDir) {
    vec3 ndc = fragLightSpace.xyz / fragLightSpace.w;
    vec2 uv = ndc.xy * 0.5 + 0.5;
    float currDepth = ndc.z * 0.5 + 0.5;

    if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0)
        return 1.0;
    if (currDepth > 1.0)
        return 1.0;

    float ndotl = max(dot(normalize(normalWorld), normalize(lightDir)), 0.0);
    float bias = max(0.001 * (1.0 - ndotl), 0.0005);

    float illumination = 0.0;
    vec2 texelSize = 1.0 / vec2(textureSize(uShadowMap, 0));

    // Rotate the poisson disk based on pixel position
    float angle = randomAngle(gl_FragCoord.xy);
    float s = sin(angle);
    float c = cos(angle);
    mat2 rot = mat2(c, -s, s, c);

    // Wider spread for softer shadows; Hardware PCF makes it buttery smooth
    float spread = 4.0;

    for (int i = 0; i < 16; ++i) {
        vec2 offset = rot * poissonDisk[i] * texelSize * spread;

        // Hardware PCF: sampler2DShadow requires a vec3(u, v, compareDepth)
        // It automatically samples 4 neighboring texels, interpolates, and returns a visibility float [0..1].
        illumination += texture(uShadowMap, vec3(uv + offset, currDepth - bias));
    }

    return illumination / 16.0;
}

void main() {
    vec3 n = normalize(vNormalWorld);
    vec3 L = normalize(uLightDirWorld);
    float diff = max(dot(n, L), 0.0);

    float shadow = shadowFactor(vFragLightSpace, n, L);

    vec3 albedo = uUseTexture != 0 ? texture(uDiffuseTex, vUv).rgb : uBaseColor;
    vec3 lit = uAmbient * albedo + uLightColor * albedo * diff * shadow;
    fragColor = vec4(lit, 1.0);
}
