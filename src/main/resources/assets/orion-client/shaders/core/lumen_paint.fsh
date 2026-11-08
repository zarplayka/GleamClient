#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:dynamictransforms.glsl>

#ifdef LUMEN_TEXTURED
uniform sampler2D Sampler0;
#endif

layout(location = 0) in vec4 tint;
layout(location = 1) in vec2 texCoord;
layout(location = 2) in vec2 localPosition;
layout(location = 3) flat in vec2 halfSize;
layout(location = 4) flat in float radius;
layout(location = 5) flat in ivec2 primitive;
layout(location = 6) in vec2 guiPosition;
layout(location = 7) flat in vec3 themeCircle;
layout(location = 0) out vec4 fragColor;

float boxDistance(vec2 position, float r) {
    vec2 corner = abs(position) - halfSize + r;
    return min(max(corner.x, corner.y), 0.0) + length(max(corner, 0.0)) - r;
}

float coverage(float distance, float pixel) {
    return clamp(0.5 - distance / pixel, 0.0, 1.0);
}

void main() {
    float r = clamp(radius, 0.0, min(halfSize.x, halfSize.y));
    float distance = boxDistance(localPosition, r);
#ifdef LUMEN_TEXTURED
    // При разных радиусах Lumen присылает отдельную команду на угол.
    if (radius > 0.0 && primitive.y >= 1 && primitive.y <= 4) {
        int corner = primitive.y;
        vec2 side = vec2(corner == 2 || corner == 3 ? 1.0 : -1.0, corner >= 3 ? 1.0 : -1.0);
        r = min(radius, 2.0 * min(halfSize.x, halfSize.y));
        distance = length(max(localPosition * side - halfSize + r, 0.0)) - r;
    }
#endif
    float pixel = max(length(vec2(dFdx(distance), dFdy(distance))), 0.001);
    vec4 color = tint;
#ifdef LUMEN_TEXTURED
    color *= texture(Sampler0, texCoord);
    if (radius > 0.0) color.a *= coverage(distance, pixel);
#else
    float parameter = texCoord.x;
    float outer = coverage(distance, pixel);
    if (primitive.x == 11) {
        color.a *= 1.0 - smoothstep(-parameter, parameter, distance);
        if (primitive.y == 1) color.a *= 1.0 - outer;
    } else if (primitive.x == 9) {
        color.a *= max(0.0, outer - coverage(distance + parameter, pixel));
    } else {
        color.a *= outer;
    }
#endif
    if (color.a <= 0.0) discard;
    if (themeCircle.z != 0.0) {
        bool inside = length(guiPosition - themeCircle.xy) < abs(themeCircle.z);
        if (inside != (themeCircle.z > 0.0)) discard;
    }
    fragColor = color * ColorModulator;
}
