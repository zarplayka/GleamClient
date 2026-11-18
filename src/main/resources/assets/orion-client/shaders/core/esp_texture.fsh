#version 330
#extension GL_ARB_separate_shader_objects : require

//? if >=26.3 {
#include <minecraft:dynamictransforms.glsl>
//?} else {
/*#moj_import <minecraft:dynamictransforms.glsl>
*///?}
//? if >=26.3 {
#include <minecraft:oit.glsl>
//?} else {

//?}

uniform sampler2D Sampler0;
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
#ifndef OIT_ALPHA_ONLY
//? if >=26.3 {
layout(location = 0) out vec4 fragColor;
//?} else {
/*out vec4 fragColor;
*///?}
#endif

void main() {
    vec4 color = texture(Sampler0, texCoord0) * vertexColor;
    if (color.a == 0.0) discard;
    color *= ColorModulator;
#ifdef OIT_ALPHA_ONLY
    executeAlphaOnlyPhase(gl_FragCoord.z, color.a);
#elif defined(OIT_ACCUMULATE)
    fragColor = sampleColorForAccumulation(color);
#else
    fragColor = color;
#endif
}
