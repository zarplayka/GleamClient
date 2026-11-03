#version 330
#extension GL_ARB_separate_shader_objects : require
layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    mat4 TextureMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
};
uniform sampler2D Sampler0;
layout(location = 0) out vec4 fragColor;
void main() {
    vec2 pixel = 1.0 / vec2(textureSize(Sampler0, 0));
    // Копия framebuffer и gl_FragCoord используют одно начало координат на каждом бэкенде.
    vec2 uv = gl_FragCoord.xy * pixel;
    vec2 direction = ColorModulator.rg * vec2(20.0, 20.0) * pixel;
    direction += (uv - vec2(0.5)) * ColorModulator.b * 0.018;
    vec2 lo = pixel * 0.5, hi = vec2(1.0) - lo;
    vec4 center = texture(Sampler0, clamp(uv,lo,hi));
    vec3 color = center.rgb * 0.24;
    color += texture(Sampler0, clamp(uv + direction*0.25,lo,hi)).rgb * 0.20;
    color += texture(Sampler0, clamp(uv - direction*0.25,lo,hi)).rgb * 0.20;
    color += texture(Sampler0, clamp(uv + direction*0.65,lo,hi)).rgb * 0.12;
    color += texture(Sampler0, clamp(uv - direction*0.65,lo,hi)).rgb * 0.12;
    color += texture(Sampler0, clamp(uv + direction,lo,hi)).rgb * 0.06;
    color += texture(Sampler0, clamp(uv - direction,lo,hi)).rgb * 0.06;
    // Пустой вспомогательный target не должен превращаться в непрозрачный чёрный слой.
    fragColor = vec4(color, center.a);
}
