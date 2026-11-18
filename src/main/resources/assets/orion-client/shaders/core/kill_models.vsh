#version 330
#extension GL_ARB_separate_shader_objects : require

//? if >=26.3 {
#include <minecraft:fog.glsl>
//?} else {
/*#moj_import <minecraft:fog.glsl>
*///?}
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
layout(location = 1) in vec2 UV0;
//?} else {
/*in vec2 UV0;
*///?}
//? if >=26.3 {
layout(location = 2) in vec4 Color;
//?} else {
/*in vec4 Color;
*///?}

//? if >=26.3 {
layout(location = 0) out float sphericalVertexDistance;
//?} else {
/*out float sphericalVertexDistance;
*///?}
//? if >=26.3 {
layout(location = 1) out float cylindricalVertexDistance;
//?} else {
/*out float cylindricalVertexDistance;
*///?}
//? if >=26.3 {
layout(location = 2) out vec4 vertexColor;
//?} else {
/*out vec4 vertexColor;
*///?}
//? if >=26.3 {
layout(location = 3) out vec2 texCoord0;
//?} else {
/*out vec2 texCoord0;
*///?}

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    sphericalVertexDistance = fog_spherical_distance(Position);
    cylindricalVertexDistance = fog_cylindrical_distance(Position);
    vertexColor = Color;
    texCoord0 = UV0;
}
