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
vec3 positionAt(vec2 uv, float depth) {
    vec2 ndc = uv * 2.0 - 1.0;
    ndc.y *= ModelOffset.y;
    float z = ModelOffset.z > 0.5 ? depth : depth * 2.0 - 1.0;
    vec4 p = TextureMat * vec4(ndc, z, 1.0);
    return p.xyz / p.w;
}
vec2 projected(vec3 p) {
    vec4 clip = ModelViewMat * vec4(p, 1.0);
    vec2 ndc = clip.xy / clip.w;
    ndc.y *= ModelOffset.y;
    return ndc * 0.5 + 0.5;
}
void main() {
    fragColor = vec4(0.0);
    vec2 uv = texCoord;
    float depth = texture(Sampler1, uv).r;
    if (depth <= 0.000001 || ColorModulator.x <= 0.0001) return;
    vec2 pixel = 1.0 / vec2(textureSize(Sampler1, 0));
    vec3 p = positionAt(uv, depth);
    if (p.z >= -0.05 || length(p) > 180.0) return;
    vec3 left = positionAt(uv - vec2(pixel.x, 0), texture(Sampler1, uv - vec2(pixel.x, 0)).r);
    vec3 right = positionAt(uv + vec2(pixel.x, 0), texture(Sampler1, uv + vec2(pixel.x, 0)).r);
    vec3 down = positionAt(uv - vec2(0, pixel.y), texture(Sampler1, uv - vec2(0, pixel.y)).r);
    vec3 up = positionAt(uv + vec2(0, pixel.y), texture(Sampler1, uv + vec2(0, pixel.y)).r);
    vec3 dx = abs(left.z - p.z) < abs(right.z - p.z) ? p - left : right - p;
    vec3 dy = abs(down.z - p.z) < abs(up.z - p.z) ? p - down : up - p;
    vec3 normal = cross(dx, dy);
    if (any(isnan(normal)) || any(isinf(normal)) || dot(normal, normal) < 0.00000001) return;
    normal = normalize(normal);
    vec3 view = normalize(-p);
    if (dot(normal, view) < 0.0) normal = -normal;
    vec3 ray = normalize(reflect(-view, normal));
    vec3 origin = p + normal * max(0.035, -p.z * 0.001);
    float stepSize = clamp(-p.z * 0.022, 0.09, 0.8);
    float qualityScale = 40.0 / ModelOffset.x;
    float t = stepSize * qualityScale;
    float previous = 0.0;
    vec2 hit = vec2(-1.0);
    float confidence = 0.0;
    for (int i = 0; i < 64; i++) {
        if (float(i) >= ModelOffset.x) break;
        vec3 q = origin + ray * t;
        if (q.z >= -0.05) break;
        vec2 sampleUv = projected(q);
        if (any(lessThan(sampleUv, vec2(0.01))) || any(greaterThan(sampleUv, vec2(0.99)))) break;
        float sceneDepth = texture(Sampler1, sampleUv).r;
        if (sceneDepth > 0.000001) {
            vec3 scenePosition = positionAt(sampleUv, sceneDepth);
            float delta = scenePosition.z - q.z;
            float thickness = max(0.08, -q.z * 0.025);
            if (i > 1 && delta >= 0.0 && delta < thickness && distance(sampleUv, uv) > pixel.x * 3.0) {
                float low = previous, high = t;
                for (int j = 0; j < 5; j++) {
                    float mid = (low + high) * 0.5;
                    vec3 mq = origin + ray * mid;
                    vec2 mu = projected(mq);
                    float md = texture(Sampler1, mu).r;
                    if (md > 0.000001 && positionAt(mu, md).z - mq.z > 0.0) high = mid; else low = mid;
                }
                hit = projected(origin + ray * high);
                confidence = 1.0 - clamp(delta / thickness, 0.0, 1.0);
                break;
            }
        }
        previous = t;
        t += stepSize * qualityScale * (1.0 + float(i) * 0.045 * qualityScale);
    }
    if (hit.x < 0.0) return;
    float border = min(min(hit.x, hit.y), min(1.0 - hit.x, 1.0 - hit.y));
    float edgeFade = smoothstep(0.01, 0.1, border);
    float fresnel = 0.3 + 0.7 * pow(1.0 - max(dot(normal, view), 0.0), 5.0);
    float weight = ColorModulator.x * fresnel * edgeFade * smoothstep(0.0, 0.35, confidence) * (1.0 - smoothstep(100.0, 180.0, length(p)));
    vec2 blur = pixel * (0.5 + ColorModulator.y * 4.0);
    vec3 reflected = texture(Sampler0, hit).rgb * 0.4;
    reflected += texture(Sampler0, clamp(hit + vec2(blur.x, 0), vec2(0.001), vec2(0.999))).rgb * 0.15;
    reflected += texture(Sampler0, clamp(hit - vec2(blur.x, 0), vec2(0.001), vec2(0.999))).rgb * 0.15;
    reflected += texture(Sampler0, clamp(hit + vec2(0, blur.y), vec2(0.001), vec2(0.999))).rgb * 0.15;
    reflected += texture(Sampler0, clamp(hit - vec2(0, blur.y), vec2(0.001), vec2(0.999))).rgb * 0.15;
    fragColor = vec4(reflected, clamp(weight, 0.0, 0.4));
}
