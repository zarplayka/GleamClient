#version 330
#extension GL_ARB_separate_shader_objects : require
// Dual Kawase: цепочка уменьшения и обратного увеличения даёт широкое гладкое размытие
// за несколько дешёвых проходов на уменьшенных текстурах.
uniform sampler2D Sampler0;
//? if >=26.3 {
layout(location = 0) out vec4 fragColor;
//?} else {
/*out vec4 fragColor;
*///?}

vec3 tap(vec2 uv, vec2 limit) {
    return texture(Sampler0, clamp(uv, limit, 1.0 - limit)).rgb;
}

void main() {
    vec2 texel = 1.0 / vec2(textureSize(Sampler0, 0));
    vec2 limit = texel * 0.5;
#ifdef GLASS_BLUR_DOWN
    // Цель вдвое меньше источника (с округлением вверх).
    vec2 uv = gl_FragCoord.xy * 2.0 * texel;
    vec2 o = texel * 1.1;
    vec3 color = tap(uv, limit) * 4.0
        + tap(uv + vec2(-o.x, -o.y), limit) + tap(uv + vec2(o.x, -o.y), limit)
        + tap(uv + vec2(-o.x, o.y), limit) + tap(uv + vec2(o.x, o.y), limit);
    fragColor = vec4(color / 8.0, 1.0);
#else
    // Цель вдвое больше источника.
    vec2 uv = gl_FragCoord.xy * 0.5 * texel;
    vec2 o = texel * 0.55;
    vec3 color = tap(uv + vec2(-o.x * 2.0, 0.0), limit) + tap(uv + vec2(o.x * 2.0, 0.0), limit)
        + tap(uv + vec2(0.0, -o.y * 2.0), limit) + tap(uv + vec2(0.0, o.y * 2.0), limit)
        + (tap(uv + vec2(-o.x, -o.y), limit) + tap(uv + vec2(o.x, -o.y), limit)
         + tap(uv + vec2(-o.x, o.y), limit) + tap(uv + vec2(o.x, o.y), limit)) * 2.0;
    fragColor = vec4(color / 12.0, 1.0);
#endif
}
