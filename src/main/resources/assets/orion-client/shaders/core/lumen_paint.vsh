#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;
layout(location = 2) in vec2 UV0;
layout(location = 3) in ivec2 UV1;
layout(location = 4) in ivec2 UV2;
layout(location = 5) in vec2 UV3;
layout(location = 6) in vec3 Normal;
layout(location = 7) in float LineWidth;

layout(location = 0) out vec4 tint;
layout(location = 1) out vec2 texCoord;
layout(location = 2) out vec2 localPosition;
layout(location = 3) flat out vec2 halfSize;
layout(location = 4) flat out float radius;
layout(location = 5) flat out ivec2 primitive;
layout(location = 6) out vec2 guiPosition;
layout(location = 7) flat out vec3 themeCircle;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    tint = Color;
    texCoord = UV0;
    localPosition = UV3;
    halfSize = vec2(UV2) / 8.0;
    ivec3 metadata = ivec3(round(Normal * 127.0));
    radius = float(metadata.y * 64 + metadata.z) / 16.0;
    primitive = ivec2(metadata.x & 15, metadata.x >> 4);
    guiPosition = Position.xy;
    themeCircle = vec3(vec2(UV1) / 4.0, LineWidth);
}
