#version 330
#extension GL_ARB_separate_shader_objects : require
layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    mat4 TextureMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
};
uniform sampler2D Sampler0;
layout(location = 0) in vec2 texCoord0;
layout(location = 1) in vec4 vertexColor;
layout(location = 0) out vec4 fragColor;
void main() {
    vec2 pixel = 1.0 / vec2(textureSize(Sampler0, 0));
    // Используем координаты полноэкранного quad: gl_FragCoord имеет разное начало на бэкендах.
    vec2 uv = texCoord0;
    vec2 direction = (vertexColor.rg * 2.0 - 1.0) * vec2(20.0, 20.0) * pixel;
    direction += (uv - vec2(0.5)) * vertexColor.b * 0.018;
    vec2 lo = pixel * 0.5, hi = vec2(1.0) - lo;
    vec3 color = texture(Sampler0, clamp(uv,lo,hi)).rgb * 0.24;
    color += texture(Sampler0, clamp(uv + direction*0.25,lo,hi)).rgb * 0.20;
    color += texture(Sampler0, clamp(uv - direction*0.25,lo,hi)).rgb * 0.20;
    color += texture(Sampler0, clamp(uv + direction*0.65,lo,hi)).rgb * 0.12;
    color += texture(Sampler0, clamp(uv - direction*0.65,lo,hi)).rgb * 0.12;
    color += texture(Sampler0, clamp(uv + direction,lo,hi)).rgb * 0.06;
    color += texture(Sampler0, clamp(uv - direction,lo,hi)).rgb * 0.06;
    fragColor = vec4(color, 1.0);
}
