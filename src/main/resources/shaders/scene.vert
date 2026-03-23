#version 330 core

layout (location = 0) in vec3 position;
layout (location = 1) in vec2 textureCoords;
layout (location = 2) in vec3 normal;

out vec2 pass_textureCoords;
out vec3 pass_normal;
out vec3 fragPos;
out vec4 fragPosLightSpace;

uniform mat4 projectionMatrix;
uniform mat4 viewMatrix;
uniform mat4 modelMatrix;
uniform mat4 lightSpaceMatrix;

void main() {
    vec4 worldPosition = modelMatrix * vec4(position, 1.0);

    pass_textureCoords = textureCoords;
    pass_normal = (modelMatrix * vec4(normal, 0.0)).xyz;
    fragPos = worldPosition.xyz;

    // Transform position into light's perspective for shadow check
    fragPosLightSpace = lightSpaceMatrix * worldPosition;

    gl_Position = projectionMatrix * viewMatrix * worldPosition;
}