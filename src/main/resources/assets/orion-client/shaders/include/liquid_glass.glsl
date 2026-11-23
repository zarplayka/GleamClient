// Общая оптика Liquid Glass для HUD, ClickGUI на Lumen и браузерного превью.
// Фон заранее размыт на GPU (glass_blur.fsh), здесь только преломление, свет и тон.
// Расстояния в пикселях framebuffer, нормаль в ориентации GUI (y вниз), наружу.

const float GLASS_BEVEL = 13.0;          // ширина скоса в GUI-пикселях
const float GLASS_REFRACTION = 0.9;      // смещение у кромки относительно скоса
const float GLASS_DISPERSION = 0.075;    // разнос каналов у кромки
const float GLASS_VIBRANCY = 1.32;       // насыщенность фона под стеклом
const vec2 GLASS_LIGHT = vec2(-0.447, -0.894);

float glassBevel(float halfMinPx, float scale) {
    return max(min(halfMinPx * 0.5, GLASS_BEVEL * scale), 1.0);
}

// Нормаль скруглённого прямоугольника. Радиус для нормали не меньше скоса,
// иначе внутри скоса появляется диагональный шов от угла.
vec2 glassNormal(vec2 position, vec2 halfSize, float radius) {
    vec2 q = abs(position) - halfSize + radius;
    vec2 corner = max(q, 0.0);
    vec2 n = length(corner) > 0.0 ? normalize(corner) : (q.x > q.y ? vec2(1.0, 0.0) : vec2(0.0, 1.0));
    return n * vec2(position.x < 0.0 ? -1.0 : 1.0, position.y < 0.0 ? -1.0 : 1.0);
}

vec3 glassSample(sampler2D scene, vec2 uv) {
    vec2 limit = 0.5 / vec2(textureSize(scene, 0));
    return texture(scene, clamp(uv, limit, 1.0 - limit)).rgb;
}

float glassNoise(vec2 position) {
    return fract(52.9829189 * fract(dot(position, vec2(0.06711056, 0.00583715))));
}

// uv: координата фона этого фрагмента; uvPerPx: шаг uv на пиксель вдоль осей GUI (со знаком).
vec3 liquidGlass(sampler2D scene, vec2 uv, vec2 uvPerPx, vec2 normal,
                 float edgePx, float bevelPx, float scale) {
    float inner = clamp(edgePx / bevelPx, 0.0, 1.0);
    float rise = 1.0 - inner;
    // Профиль выпуклой линзы: середина плоская, у кромки фон быстро подтягивается изнутри.
    float lens = 1.0 - sqrt(max(1.0 - rise * rise, 0.0));
    float shift = bevelPx * GLASS_REFRACTION * lens;
    vec2 inward = -normal * uvPerPx;
    vec2 base = uv + inward * shift;

    vec3 color;
    float spread = shift * GLASS_DISPERSION;
    if (spread > 0.25) {
        color = vec3(glassSample(scene, base - inward * spread).r,
                     glassSample(scene, base).g,
                     glassSample(scene, base + inward * spread).b);
    } else {
        color = glassSample(scene, base);
    }

    float luminance = dot(color, vec3(0.2126, 0.7152, 0.0722));
    color = max(mix(vec3(luminance), color, GLASS_VIBRANCY), 0.0);
    // Светлый фон приглушаем сильнее, чтобы белый текст оставался читаемым.
    float veil = mix(0.12, 0.44, smoothstep(0.28, 0.92, luminance));
    color = mix(color, vec3(0.045, 0.055, 0.07), veil);

    float facing = dot(normal, GLASS_LIGHT);
    float lit = max(facing, 0.0), back = max(-facing, 0.0);
    float rim = 1.0 - smoothstep(0.0, max(1.3 * scale, 1.0), edgePx);
    float specular = rim * (0.62 * pow(lit, 1.6) + 0.28 * pow(back, 2.0) + 0.07);
    float sheen = rise * rise * rise * (0.12 * lit + 0.025);
    color *= 1.0 - 0.16 * pow(rise, 2.5) * back;
    color += (1.0 - color) * clamp(specular + sheen, 0.0, 1.0);
    // Дизеринг убирает полосы на плавном размытии.
    return color + (glassNoise(gl_FragCoord.xy) - 0.5) / 255.0;
}
