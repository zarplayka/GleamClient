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
#include <minecraft:oit.glsl>
//?} else {

//?}

//? if >=26.3 {
layout(location = 0) in float sphericalVertexDistance;
//?} else {
/*in float sphericalVertexDistance;
*///?}
//? if >=26.3 {
layout(location = 1) in float cylindricalVertexDistance;
//?} else {
/*in float cylindricalVertexDistance;
*///?}
//? if >=26.3 {
layout(location = 2) in vec4 vertexColor;
//?} else {
/*in vec4 vertexColor;
*///?}
//? if >=26.3 {
layout(location = 3) in vec2 texCoord0;
//?} else {
/*in vec2 texCoord0;
*///?}
#ifndef OIT_ALPHA_ONLY
//? if >=26.3 {
layout(location = 0) out vec4 fragColor;
//?} else {
/*out vec4 fragColor;
*///?}
#endif
uniform sampler2D Sampler0;

void main() {
    vec4 color = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;
    float visibility = 1.0 - total_fog_value(sphericalVertexDistance, cylindricalVertexDistance,
        FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd);
    color.a *= visibility;
    // Невидимые части не должны оставлять невидимую преграду в depth buffer.
    if (color.a < 0.01) discard;
#ifdef OIT_ALPHA_ONLY
    executeAlphaOnlyPhase(gl_FragCoord.z, color.a);
#elif defined(OIT_ACCUMULATE)
    fragColor = sampleColorForAccumulation(color);
#else
    fragColor = color;
#endif
}
