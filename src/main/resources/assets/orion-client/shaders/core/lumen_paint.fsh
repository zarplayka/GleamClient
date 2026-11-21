#version 330
#extension GL_ARB_separate_shader_objects : require

//? if >=26.3 {
#include <minecraft:dynamictransforms.glsl>
//?} else {
/*#moj_import <minecraft:dynamictransforms.glsl>
*///?}

#if defined(LUMEN_TEXTURED) || defined(LUMEN_GLASS)
uniform sampler2D Sampler0;
#endif

#ifdef LUMEN_GLASS
//? if >=26.3 {
#include <orion-client:liquid_glass.glsl>
//?} else {
/*#moj_import <orion-client:liquid_glass.glsl>
*///?}
#endif

//? if >=26.3 {
layout(location = 0) in vec4 tint;
//?} else {
/*in vec4 tint;
*///?}
//? if >=26.3 {
layout(location = 1) in vec2 texCoord;
//?} else {
/*in vec2 texCoord;
*///?}
//? if >=26.3 {
layout(location = 2) in vec2 localPosition;
//?} else {
/*in vec2 localPosition;
*///?}
//? if >=26.3 {
layout(location = 3) flat in vec2 halfSize;
//?} else {
/*flat in vec2 halfSize;
*///?}
//? if >=26.3 {
layout(location = 4) flat in float radius;
//?} else {
/*flat in float radius;
*///?}
//? if >=26.3 {
layout(location = 5) flat in ivec2 primitive;
//?} else {
/*flat in ivec2 primitive;
*///?}
//? if >=26.3 {
layout(location = 6) in vec2 guiPosition;
//?} else {
/*in vec2 guiPosition;
*///?}
//? if >=26.3 {
layout(location = 7) flat in vec3 themeCircle;
//?} else {
/*flat in vec3 themeCircle;
*///?}
//? if >=26.3 {
layout(location = 0) out vec4 fragColor;
//?} else {
/*out vec4 fragColor;
*///?}

float boxDistance(vec2 position, float r) {
    vec2 corner = abs(position) - halfSize + r;
    return min(max(corner.x, corner.y), 0.0) + length(max(corner, 0.0)) - r;
}

float coverage(float distance, float pixel) {
    return clamp(0.5 - distance / pixel, 0.0, 1.0);
}

#ifdef LUMEN_TEXTURED
// Lumen command 12 uses block coordinates and a data texture, not normalized image UVs.
// The first 16 RGBA texels store little-endian floats. The rest form a premultiplied color ramp.
float gradientParameter(int index) {
    uvec4 rgba = uvec4(round(texelFetch(Sampler0, ivec2(index, 0), 0) * 255.0));
    return uintBitsToFloat(rgba.x | rgba.y << 8u | rgba.z << 16u | rgba.w << 24u);
}

vec4 sampleGradient(vec2 position) {
    vec2 tileSize = vec2(gradientParameter(9), gradientParameter(10));
    if (all(greaterThan(tileSize, vec2(0.0)))) {
        position -= vec2(gradientParameter(11), gradientParameter(12));
        if (gradientParameter(13) > 0.5) {
            position = mod(position, tileSize);
        } else if (any(lessThan(position, vec2(0.0))) || any(greaterThanEqual(position, tileSize))) {
            return vec4(0.0);
        }
    }

    int shape = int(gradientParameter(0));
    vec2 delta = position - vec2(gradientParameter(2), gradientParameter(3));
    vec2 axis = vec2(gradientParameter(4), gradientParameter(5));
    float coordinate = shape == 0 ? dot(delta, axis)
        : shape == 1 ? length(delta * vec2(1.0, axis.y))
        : fract((atan(delta.x, -delta.y) - axis.x) / (2.0 * 3.141592653589793));
    float start = gradientParameter(6);
    float lengthOfRamp = gradientParameter(7) - start;
    float fraction = step(start, coordinate);
    if (lengthOfRamp > 0.000001) {
        fraction = (coordinate - start) / lengthOfRamp;
        fraction = gradientParameter(1) > 0.5 ? fract(fraction) : clamp(fraction, 0.0, 1.0);
    }

    int end = clamp(int(gradientParameter(8)), 2, textureSize(Sampler0, 0).x - 16) - 1;
    float sampleIndex = fraction * float(end);
    int left = int(sampleIndex);
    vec4 premultiplied = mix(texelFetch(Sampler0, ivec2(16 + left, 0), 0),
        texelFetch(Sampler0, ivec2(16 + min(left + 1, end), 0), 0), fract(sampleIndex));
    if (premultiplied.a <= 0.0) return vec4(0.0);
    return vec4(premultiplied.rgb / premultiplied.a, premultiplied.a);
}
#endif

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
    vec2 distanceGradient = vec2(dFdx(distance), dFdy(distance));
    float pixel = max(length(distanceGradient), 0.001);
    vec4 color = tint;
#ifdef LUMEN_GLASS
    vec2 normal = distanceGradient / pixel;
    bool panorama = primitive.x == 1;
    float vertical = panorama ? sign(dFdy(texCoord.y)) : (tint.r > 0.998 ? -1.0 : 1.0);
    vec2 lightNormal = vec2(normal.x, normal.y * vertical);
    vec2 sampleStep = panorama ? abs(vec2(dFdx(texCoord.x), dFdy(texCoord.y))) : 1.0 / vec2(textureSize(Sampler0, 0));
    vec2 uv = panorama ? texCoord : gl_FragCoord.xy * sampleStep;
    vec2 refraction = panorama ? lightNormal : normal;
    float depth = clamp(min(halfSize.x, halfSize.y) * 0.24 / pixel, 4.0, 17.0);
    float bevel = clamp(-distance / (pixel * depth), 0.0, 1.0);
    color = vec4(liquidGlass(Sampler0, uv, sampleStep, refraction, lightNormal, bevel), tint.a * coverage(distance, pixel));
#elif defined(LUMEN_TEXTURED)
    color *= primitive.x == 12 ? sampleGradient(texCoord) : texture(Sampler0, texCoord);
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
