#version 330 core
layout (location = 0) in vec3 aPos;
layout (location = 1) in vec3 aNormal;
layout (location = 2) in vec2 aUv;

uniform mat4 uModel;
uniform mat4 uView;
uniform mat4 uProjection;
uniform mat4 uLightSpaceMatrix;

out vec3 vNormalWorld;
out vec3 vFragWorld;
out vec4 vFragLightSpace;
out vec2 vUv;

void main() {
    vec4 worldPos = uModel * vec4(aPos, 1.0);
    vFragWorld = worldPos.xyz;
    mat3 normalMat = mat3(transpose(inverse(uModel)));
    vNormalWorld = normalize(normalMat * aNormal);
    vFragLightSpace = uLightSpaceMatrix * worldPos;
    vUv = aUv;
    gl_Position = uProjection * uView * worldPos;
}
