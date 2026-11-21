// Coordinates and displacements are in framebuffer pixels before conversion to UVs.
vec3 glassSample(sampler2D scene, vec2 uv, vec2 texel) {
    return texture(scene, clamp(uv, texel * 0.5, 1.0 - texel * 0.5)).rgb;
}

vec3 liquidGlass(sampler2D scene, vec2 uv, vec2 pixel, vec2 normal, vec2 lightNormal, float bevel) {
    float edge = 1.0 - smoothstep(0.0, 1.0, bevel);
    uv -= normal * pixel * (19.0 * edge * (0.52 + 0.48 * edge));
    vec2 spread = pixel * mix(5.4, 2.5, edge);
    vec3 center = glassSample(scene, uv, pixel);
    vec3 soft = glassSample(scene, uv + spread * vec2(1.0, 0.5), pixel)
        + glassSample(scene, uv + spread * vec2(-0.5, 1.0), pixel)
        + glassSample(scene, uv + spread * vec2(-1.0, -0.5), pixel)
        + glassSample(scene, uv + spread * vec2(0.5, -1.0), pixel);
    vec3 backdrop = mix(center, soft * 0.25, mix(0.72, 0.35, edge));
    float luminance = dot(backdrop, vec3(0.2126, 0.7152, 0.0722));
    float tint = mix(0.14, 0.67, smoothstep(0.13, 0.88, luminance));
    vec3 glass = mix(backdrop, vec3(0.061, 0.078, 0.10), tint);

    float light = dot(lightNormal, vec2(-0.50, -0.866));
    float rim = pow(edge, 3.6);
    float highlight = pow(max(light, 0.0), 2.4);
    float rebound = pow(max(-light, 0.0), 3.0);
    glass *= 1.0 - 0.16 * edge * rebound;
    glass += vec3(0.58, 0.72, 0.85) * (0.14 * highlight + 0.045 * rebound) * edge;
    return mix(glass, vec3(0.91, 0.96, 1.0), rim * (0.35 * highlight + 0.23 * rebound));
}
