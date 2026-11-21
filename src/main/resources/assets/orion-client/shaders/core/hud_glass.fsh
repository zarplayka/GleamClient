#version 330
#extension GL_ARB_separate_shader_objects : require

//? if >=26.3 {
layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    mat4 TextureMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
};
//?} else {
/*layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};
*///?}

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform sampler2D Sampler2;

//? if >=26.3 {
layout(location = 0) in vec2 texCoord0;
//?} else {
/*in vec2 texCoord0;
*///?}
//? if >=26.3 {
layout(location = 1) in vec4 vertexColor;
//?} else {
/*in vec4 vertexColor;
*///?}
//? if >=26.3 {
layout(location = 0) out vec4 fragColor;
//?} else {
/*out vec4 fragColor;
*///?}

//? if >=26.3 {
#include <orion-client:liquid_glass.glsl>
//?} else {
/*#moj_import <orion-client:liquid_glass.glsl>
*///?}

void main() {
    vec4 foreground = texture(Sampler0, texCoord0);
    vec4 shape = texture(Sampler2, texCoord0);
    float coverage = shape.a;
    // Альфа вершины выбирает обычную поверхность без копирования и размытия мира.
    if (vertexColor.a < 0.5) {
        float baseAlpha = coverage * 0.94;
        float alpha = foreground.a + baseAlpha * (1.0 - foreground.a);
        if (alpha <= 0.001) discard;
        vec3 color = (foreground.rgb * foreground.a
            + vec3(0.065, 0.07, 0.085) * baseAlpha * (1.0 - foreground.a)) / alpha;
        fragColor = vec4(color, alpha) * ColorModulator;
        return;
    }
    if (coverage <= 0.001) {
        if (foreground.a <= 0.001) discard;
        fragColor = foreground * vertexColor * ColorModulator;
        return;
    }

    vec2 pixel = 1.0 / vec2(textureSize(Sampler1, 0));
    vec2 normal = shape.gb * 2.0 - 1.0;
    vec2 screenNormal = vec2(normal.x, vertexColor.r > 0.998 ? -normal.y : normal.y);
    vec3 glass = liquidGlass(Sampler1, gl_FragCoord.xy * pixel, pixel, screenNormal, normal, shape.r);
    float glassAlpha = coverage;
    float alpha = foreground.a + glassAlpha * (1.0 - foreground.a);
    vec3 premultiplied = foreground.rgb * foreground.a
            + glass * glassAlpha * (1.0 - foreground.a);
    fragColor = vec4(premultiplied / max(alpha, 0.001), alpha) * vertexColor * ColorModulator;
}
