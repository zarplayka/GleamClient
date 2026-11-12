#version 330
#extension GL_ARB_separate_shader_objects : require

//? if >=26.3 {
#include <minecraft:dynamictransforms.glsl>
//?} else {
/*#moj_import <minecraft:dynamictransforms.glsl>
*///?}
//? if >=26.3 {
#include <minecraft:projection.glsl>
//?} else {
/*#moj_import <minecraft:projection.glsl>
*///?}

//? if >=26.3 {
layout(location = 0) in vec3 Position;
//?} else {
/*in vec3 Position;
*///?}
//? if >=26.3 {
layout(location = 1) in vec4 Color;
//?} else {
/*in vec4 Color;
*///?}
//? if >=26.3 {
layout(location = 2) in vec2 UV0;
//?} else {
/*in vec2 UV0;
*///?}
//? if >=26.3 {
layout(location = 3) in ivec2 UV1;
//?} else {
/*in ivec2 UV1;
*///?}
//? if >=26.3 {
layout(location = 4) in ivec2 UV2;
//?} else {
/*in ivec2 UV2;
*///?}
//? if >=26.3 {
layout(location = 5) in vec2 UV3;
//?} else {
/*in vec2 UV3;
*///?}
//? if >=26.3 {
layout(location = 6) in vec3 Normal;
//?} else {
/*in vec3 Normal;
*///?}
//? if >=26.3 {
layout(location = 7) in float LineWidth;
//?} else {
/*in float LineWidth;
*///?}

//? if >=26.3 {
layout(location = 0) out vec4 tint;
//?} else {
/*out vec4 tint;
*///?}
//? if >=26.3 {
layout(location = 1) out vec2 texCoord;
//?} else {
/*out vec2 texCoord;
*///?}
//? if >=26.3 {
layout(location = 2) out vec2 localPosition;
//?} else {
/*out vec2 localPosition;
*///?}
//? if >=26.3 {
layout(location = 3) flat out vec2 halfSize;
//?} else {
/*flat out vec2 halfSize;
*///?}
//? if >=26.3 {
layout(location = 4) flat out float radius;
//?} else {
/*flat out float radius;
*///?}
//? if >=26.3 {
layout(location = 5) flat out ivec2 primitive;
//?} else {
/*flat out ivec2 primitive;
*///?}
//? if >=26.3 {
layout(location = 6) out vec2 guiPosition;
//?} else {
/*out vec2 guiPosition;
*///?}
//? if >=26.3 {
layout(location = 7) flat out vec3 themeCircle;
//?} else {
/*flat out vec3 themeCircle;
*///?}

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
