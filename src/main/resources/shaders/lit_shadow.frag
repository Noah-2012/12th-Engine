#version 330 core
in vec3 vNormalWorld;
in vec3 vFragWorld;
in vec4 vFragLightSpace;
in vec2 vUv;

uniform vec3 uLightDirWorld;
uniform vec3 uLightColor;
uniform vec3 uAmbient;
uniform sampler2D uDiffuseTex;
uniform sampler2D uShadowMap;
uniform int uUseTexture;
uniform vec3 uBaseColor;

out vec4 fragColor;

float shadowFactor(vec4 fragLightSpace, vec3 normalWorld, vec3 lightDir) {
    vec3 ndc = fragLightSpace.xyz / fragLightSpace.w;
    vec2 uv = ndc.xy * 0.5 + 0.5;
    float currDepth = ndc.z * 0.5 + 0.5;
    if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0)
        return 1.0;
    if (currDepth > 1.0)
        return 1.0;

    float ndotl = max(dot(normalize(normalWorld), normalize(lightDir)), 0.0);
    float bias = max(0.0000005 * (1.0 - ndotl), 0.0005);

    float shadow = 0.0;
    vec2 texel = 1.6 / textureSize(uShadowMap, 0);
    for (int x = -2; x <= 2; ++x) {
        for (int y = -2; y <= 2; ++y) {
            float pcfDepth = texture(uShadowMap, uv + vec2(x, y) * texel).r;
            shadow += currDepth - bias > pcfDepth ? 1.0 : 0.0;
        }
    }
    shadow /= 25.0;
    return 1.0 - shadow;
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