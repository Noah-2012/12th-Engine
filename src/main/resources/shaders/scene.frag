#version 330 core

in vec2 pass_textureCoords;
in vec3 pass_normal;
in vec3 fragPos;
in vec4 fragPosLightSpace;

out vec4 out_Color;

uniform sampler2D modelTexture; // Texture Unit 0
uniform sampler2D shadowMap;    // Texture Unit 1

uniform vec3 lightPosition;
uniform vec3 lightColor;
uniform float lightIntensity;

float calculateShadow(vec4 fragPosLS) {
    // Perspective divide
    vec3 projCoords = fragPosLS.xyz / fragPosLS.w;
    // Map to [0,1] range
    projCoords = projCoords * 0.5 + 0.5;

    // Get depth from shadow map
    float closestDepth = texture(shadowMap, projCoords.xy).r;
    float currentDepth = projCoords.z;

    // Bias to prevent shadow acne
    float bias = 0.005;
    return currentDepth - bias > closestDepth ? 0.5 : 1.0;
}

void main() {
    vec4 texColor = texture(modelTexture, pass_textureCoords);

    vec3 unitNormal = normalize(pass_normal);
    vec3 lightDir = normalize(lightPosition - fragPos);

    float nDotl = dot(unitNormal, lightDir);
    float brightness = max(nDotl, 0.0);

    float shadow = calculateShadow(fragPosLightSpace);

    vec3 diffuse = (shadow * brightness) * lightColor * lightIntensity;
    vec3 ambient = vec3(0.2) * lightColor;

    out_Color = vec4(ambient + diffuse, 1.0) * texColor;
}