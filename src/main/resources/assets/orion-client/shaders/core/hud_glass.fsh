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
    vec2 refraction = normal;
    if (vertexColor.r > 0.998) refraction.y = -refraction.y;
    float bevel = 1.0 - smoothstep(0.0, 1.0, shape.r);
    float lens = 0.65 * bevel + 0.35 * bevel * bevel;
    vec2 screenUv = gl_FragCoord.xy * pixel - refraction * pixel * (16.0 * lens);
    vec2 uvMin = pixel * 0.5;
    vec2 uvMax = vec2(1.0) - uvMin;
    vec2 wide = pixel * mix(9.5, 5.0, bevel);
    vec2 near = wide * 0.52;
    vec3 core = texture(Sampler1, clamp(screenUv, uvMin, uvMax)).rgb;
    vec3 inner = texture(Sampler1, clamp(screenUv + vec2(near.x, 0.0), uvMin, uvMax)).rgb
        + texture(Sampler1, clamp(screenUv - vec2(near.x, 0.0), uvMin, uvMax)).rgb
        + texture(Sampler1, clamp(screenUv + vec2(0.0, near.y), uvMin, uvMax)).rgb
        + texture(Sampler1, clamp(screenUv - vec2(0.0, near.y), uvMin, uvMax)).rgb;
    vec3 outer = texture(Sampler1, clamp(screenUv + wide, uvMin, uvMax)).rgb
        + texture(Sampler1, clamp(screenUv - wide, uvMin, uvMax)).rgb
        + texture(Sampler1, clamp(screenUv + vec2(wide.x, -wide.y), uvMin, uvMax)).rgb
        + texture(Sampler1, clamp(screenUv + vec2(-wide.x, wide.y), uvMin, uvMax)).rgb;
    vec3 blurred = core * 0.22 + inner * 0.125 + outer * 0.07;
    float luminance = dot(blurred, vec3(0.2126, 0.7152, 0.0722));
    float tint = mix(0.13, 0.48, smoothstep(0.18, 0.90, luminance));
    vec3 glass = mix(blurred, vec3(0.052, 0.071, 0.09), tint);
    float keyLight = clamp(dot(normal, vec2(0.62, 0.78)), -1.0, 1.0);
    float lowerGlint = pow(max(0.0, keyLight), 1.8);
    float upperGlint = pow(max(0.0, -keyLight), 2.4);
    float edge = pow(bevel, 3.2);
    glass *= 1.0 - 0.23 * edge * upperGlint;
    glass += vec3(0.72, 0.86, 0.97) * bevel * (0.24 * lowerGlint + 0.07 * upperGlint);
    glass = mix(glass, vec3(0.91, 0.97, 1.0), edge * (0.38 * lowerGlint + 0.14 * upperGlint));
    float glassAlpha = coverage * mix(0.87, 0.98, bevel);
    float alpha = foreground.a + glassAlpha * (1.0 - foreground.a);
    vec3 premultiplied = foreground.rgb * foreground.a
            + glass * glassAlpha * (1.0 - foreground.a);
    fragColor = vec4(premultiplied / max(alpha, 0.001), alpha) * vertexColor * ColorModulator;
}
