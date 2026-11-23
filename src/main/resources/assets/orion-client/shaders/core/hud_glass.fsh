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
        fragColor = foreground * ColorModulator;
        return;
    }

    // Цвет вершины несёт параметры: r — начало координат OpenGL, g — масштаб, b — ширина скоса.
    float scale = max(vertexColor.g * (255.0 / 32.0), 0.25);
    float bevel = max(vertexColor.b * (255.0 / 4.0), 1.0);
    // Размытый фон хранится в половинном разрешении framebuffer.
    vec2 screen = vec2(textureSize(Sampler1, 0)) * 2.0;
    vec2 uvPerPx = vec2(1.0, vertexColor.r > 0.5 ? -1.0 : 1.0) / screen;
    // Маска: r — расстояние до края (до 96 px), gb — нормаль наружу в ориентации GUI.
    vec2 normal = shape.gb * 2.0 - 1.0;
    vec3 glass = liquidGlass(Sampler1, gl_FragCoord.xy / screen, uvPerPx, normal, shape.r * 96.0, bevel, scale);
    float alpha = foreground.a + coverage * (1.0 - foreground.a);
    vec3 premultiplied = foreground.rgb * foreground.a + glass * coverage * (1.0 - foreground.a);
    fragColor = vec4(premultiplied / max(alpha, 0.001), alpha) * ColorModulator;
}
