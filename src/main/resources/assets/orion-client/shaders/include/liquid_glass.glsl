// Coordinates and displacements are in framebuffer pixels before conversion to UVs.
vec3 glassSample(sampler2D scene, vec2 uv, vec2 texel) {
    return texture(scene, clamp(uv, texel * 0.5, 1.0 - texel * 0.5)).rgb;
}

vec3 liquidGlass(sampler2D scene, vec2 uv, vec2 pixel, vec2 normal, vec2 lightNormal, float bevel) {
    float edge = 1.0 - smoothstep(0.0, 1.0, bevel);
    uv -= normal * pixel * (22.0 * edge);
    vec2 spread = pixel * mix(4.2, 1.8, edge);
    vec3 backdrop = glassSample(scene, uv, pixel) * 0.4
        + (glassSample(scene, uv + vec2(spread.x, 0.0), pixel)
         + glassSample(scene, uv - vec2(spread.x, 0.0), pixel)
         + glassSample(scene, uv + vec2(0.0, spread.y), pixel)
         + glassSample(scene, uv - vec2(0.0, spread.y), pixel)) * 0.15;
    float luminance = dot(backdrop, vec3(0.2126, 0.7152, 0.0722));
    float tint = mix(0.03, 0.26, smoothstep(0.32, 0.92, luminance));
    vec3 glass = backdrop * (1.0 - tint);

    float light = dot(lightNormal, vec2(-0.60, -0.80));
    float rim = pow(edge, 4.0);
    float highlight = pow(max(light, 0.0), 2.4);
    float rebound = pow(max(-light, 0.0), 3.0);
    glass *= 1.0 - 0.10 * edge * rebound;
    return mix(glass, vec3(0.94, 0.98, 1.0), rim * (0.58 * highlight + 0.21 * rebound));
}
