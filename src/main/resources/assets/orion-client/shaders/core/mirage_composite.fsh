#version 330
#extension GL_ARB_separate_shader_objects : require
layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    mat4 TextureMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
};
uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;
vec3 bright(vec2 uv) {
    vec3 color = texture(Sampler0, clamp(uv, vec2(0.001), vec2(0.999))).rgb;
    return color * smoothstep(0.65, 0.95, max(max(color.r, color.g), color.b));
}
void main() {
    vec4 original = texture(Sampler0, texCoord);
    if (original.a <= 0.001) { fragColor = original; return; }
    vec4 reflected = texture(Sampler1, texCoord);
    vec3 color = mix(original.rgb, reflected.rgb, reflected.a);
    if (ColorModulator.z > 0.0001) {
        vec2 offset = 4.0 / vec2(textureSize(Sampler0, 0));
        vec3 glow = bright(texCoord + offset) + bright(texCoord - offset);
        glow += bright(texCoord + vec2(offset.x, -offset.y)) + bright(texCoord + vec2(-offset.x, offset.y));
        color += glow * (ColorModulator.z * 0.25) * (vec3(1.0) - color);
    }
    float grade = ColorModulator.w;
    float luminance = dot(color, vec3(0.2126, 0.7152, 0.0722));
    color = mix(vec3(luminance), color, 1.0 + grade * 0.18);
    color *= vec3(1.0 + grade * 0.035, 1.0 + grade * 0.01, 1.0 - grade * 0.025);
    color = (color - 0.5) * (1.0 + grade * 0.07) + 0.5;
    fragColor = vec4(clamp(color, 0.0, 1.0), original.a);
}
