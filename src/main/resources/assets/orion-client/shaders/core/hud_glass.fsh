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

const float BLUR_OFFSET = 7.0;

void main() {
    vec4 foreground = texture(Sampler0, texCoord0);
    float coverage = texture(Sampler2, texCoord0).a;
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
    vec2 maskStep = 1.8 / vec2(textureSize(Sampler2, 0));
    float left = texture(Sampler2, texCoord0 - vec2(maskStep.x, 0.0)).a;
    float right = texture(Sampler2, texCoord0 + vec2(maskStep.x, 0.0)).a;
    float top = texture(Sampler2, texCoord0 - vec2(0.0, maskStep.y)).a;
    float bottom = texture(Sampler2, texCoord0 + vec2(0.0, maskStep.y)).a;
    vec2 rimDirection = vec2(right - left, bottom - top);
    vec2 screenUv = gl_FragCoord.xy * pixel + rimDirection * pixel * 2.5;
    vec2 uvMin = pixel * 0.5;
    vec2 uvMax = vec2(1.0) - uvMin;
    vec2 offset = pixel * BLUR_OFFSET;
    // Пять билинейных выборок вместо 49 выборок с exp на каждом пикселе панели.
    vec3 blurred = texture(Sampler1, clamp(screenUv, uvMin, uvMax)).rgb * 0.5;
    blurred += texture(Sampler1, clamp(screenUv + offset, uvMin, uvMax)).rgb * 0.125;
    blurred += texture(Sampler1, clamp(screenUv - offset, uvMin, uvMax)).rgb * 0.125;
    blurred += texture(Sampler1, clamp(screenUv + vec2(offset.x, -offset.y), uvMin, uvMax)).rgb * 0.125;
    blurred += texture(Sampler1, clamp(screenUv + vec2(-offset.x, offset.y), uvMin, uvMax)).rgb * 0.125;
    float luminance = dot(blurred, vec3(0.2126, 0.7152, 0.0722));
    // У яркого фона сильнее затемняется середина. Кромка сохраняет отражённый свет.
    float tint = mix(0.18, 0.70, smoothstep(0.18, 0.85, luminance));
    vec3 glass = mix(mix(vec3(luminance), blurred, 0.72), vec3(0.055, 0.071, 0.088), tint);
    float rim = clamp(dot(rimDirection, vec2(0.45, 0.88)), 0.0, 1.0);
    glass += vec3(0.27, 0.31, 0.35) * rim;
    float glassAlpha = coverage * 0.91;
    float alpha = foreground.a + glassAlpha * (1.0 - foreground.a);
    vec3 premultiplied = foreground.rgb * foreground.a
            + glass * glassAlpha * (1.0 - foreground.a);
    fragColor = vec4(premultiplied / max(alpha, 0.001), alpha) * vertexColor * ColorModulator;
}
