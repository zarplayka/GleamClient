package ru.orion.client.effect.kill;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import static ru.orion.client.effect.kill.OrionKillGeometry.alpha;
import static ru.orion.client.effect.kill.OrionKillGeometry.box;
import static ru.orion.client.effect.kill.OrionKillGeometry.quad;
import static ru.orion.client.effect.kill.OrionKillGeometry.shade;
import static ru.orion.client.effect.kill.OrionKillGeometry.smooth;

/** Две объёмные сцены с пиксельными силуэтами и блочной геометрией. */
public final class OrionArcaneKillModels {
    private static final float TAU = (float) (Math.PI * 2);
    private static final float PIXEL = .105F;
    // Ряды от острия вверх, одинаковые цвета объединяются в прямоугольные участки.
    private static final String[] SWORD = {
            "....X....", "...XCX...", "...XLX...", "..XCLCX..", "..XCLCX..", "..XCLCX..",
            "..XCLCX..", "..XCLCX..", "..XCLCX..", "..XCLCX..", "..XCLCX..", "..XCDCX..",
            ".XXDDDXX.", "XXCCCCCXX", "XCCDDDCCX", "XXXHHHXXX", "...XHX...", "...XHX...",
            "...XHX...", "...XHX...", "...XXX...", "...XCX...", "....X...."
    };

    private OrionArcaneKillModels() { }

    public static float duration(int type) {
        return switch (type) {
            case 2 -> 3.2F;
            case 4 -> 3.4F;
            default -> throw new IllegalArgumentException("Unknown 3D kill effect: " + type);
        };
    }

    public static void render(int type, PoseStack stack, VertexConsumer vertices, float seconds,
                              int quality, float particles, long seed) {
        float t = seconds / duration(type);
        if (t <= 0 || t >= 1) return;
        stack.pushPose();
        if (type == 2) swordRain(stack, vertices, t, quality, particles, seed);
        else blackHole(stack, vertices, t, quality, particles, seed);
        stack.popPose();
    }

    private static void swordRain(PoseStack stack, VertexConsumer vertices, float t, int quality, float particles, long seed) {
        int count = quality == 0 ? 3 : 5;
        for (int i = 0; i < count; i++) {
            float delay = i == 0 ? .02F : .13F + i * .047F;
            float elapsed = t - delay;
            if (elapsed <= 0) continue;
            float fall = smooth(elapsed / .19F);
            float fade = smooth(elapsed / .025F) * (1 - smooth((t - .78F) / .22F));
            float angle = i * TAU / (count - 1);
            float radius = i == 0 ? 0 : 1.08F;
            float x = (float) Math.cos(angle) * radius, z = (float) Math.sin(angle) * radius;
            stack.pushPose();
            stack.translate(x, 4.8F * (1 - fall) - .13F, z);
            stack.rotate(Axis.YP.rotationDegrees(i * 49 + 18));
            stack.rotate(Axis.ZP.rotationDegrees(i == 0 ? -4 : i % 2 == 0 ? 9 : -9));
            float size = i == 0 ? 1.05F : .77F;
            stack.scale(size, size, size);
            sword(stack, vertices, fade);
            stack.popPose();

            float impact = (elapsed - .19F) / .24F;
            if (impact > 0 && impact < 1) {
                int chips = (int) (particles * (6 + quality * 3));
                for (int j = 0; j < chips; j++) {
                    float theta = random(seed + i * 31L, j) * TAU;
                    float distance = impact * (.35F + random(seed, j + 8) * .8F);
                    float y = .04F + (float) Math.sin(impact * Math.PI) * .35F;
                    float chip = .04F * (1 - impact);
                    box(stack.last(), vertices, x + (float) Math.cos(theta) * distance, y,
                            z + (float) Math.sin(theta) * distance, chip, chip, chip,
                            alpha(j % 3 == 0 ? 0xFFFFE5AA : 0xFF8BDBD1, (1 - impact) * fade));
                }
            }
        }
    }

    private static void sword(PoseStack stack, VertexConsumer vertices, float fade) {
        for (int row = 0; row < SWORD.length; row++) {
            String pixels = SWORD[row];
            for (int x = 0; x < pixels.length();) {
                char material = pixels.charAt(x);
                int end = x + 1;
                while (end < pixels.length() && pixels.charAt(end) == material) end++;
                if (material != '.') {
                    int color = switch (material) {
                        case 'L' -> 0xFFD5FFFF;
                        case 'C' -> 0xFF42C9C4;
                        case 'D' -> 0xFF206A70;
                        case 'H' -> 0xFF865C39;
                        default -> 0xFF173238;
                    };
                    box(stack.last(), vertices, ((x + end) / 2F - pixels.length() / 2F) * PIXEL,
                            (row + .5F) * PIXEL, 0, (end - x) * PIXEL, PIXEL, PIXEL, alpha(color, fade));
                }
                x = end;
            }
        }
    }

    private static void blackHole(PoseStack stack, VertexConsumer vertices, float t, int quality, float particles, long seed) {
        float envelope = smooth(t / .18F) * (1 - smooth((t - .76F) / .24F));
        float turn = t * 180;
        stack.pushPose();
        stack.translate(0, 1.2F, 0);
        stack.rotate(Axis.YP.rotationDegrees(turn));
        stack.pushPose();
        float coreSize = .13F * envelope;
        stack.scale(coreSize, coreSize, coreSize);
        voxelCore(stack, vertices);
        stack.popPose();

        for (int band = 0; band < 2; band++) {
            stack.pushPose();
            stack.rotate(Axis.ZP.rotationDegrees(band == 0 ? 24 : -37));
            stack.rotate(Axis.XP.rotationDegrees(band == 0 ? 18 : 70));
            int blocks = 16 + quality * 4;
            for (int i = 0; i < blocks; i++) {
                float angle = i * TAU / blocks + turn * .025F * (band == 0 ? 1 : -1);
                float radius = (.69F + band * .21F) * envelope;
                float width = (.09F + i % 3 * .01F) * envelope;
                int color = i % 4 == 0 ? 0xFFD4B4FF : band == 0 ? 0xFF9F68E5 : 0xFF673D9C;
                box(stack.last(), vertices, (float) Math.cos(angle) * radius, 0, (float) Math.sin(angle) * radius,
                        width, width * .58F, width, alpha(color, envelope));
            }
            stack.popPose();
        }

        int debris = (int) (particles * (12 + quality * 7));
        for (int i = 0; i < debris; i++) {
            float progress = (random(seed, i) + t * 1.8F) % 1;
            float radius = (.5F + (1 - progress) * 1.6F) * envelope;
            float angle = random(seed, i + 100) * TAU + progress * 3.8F;
            float height = (random(seed, i + 200) - .5F) * (1 - progress) * 2;
            float size = (.06F + random(seed, i + 300) * .07F) * (1 - progress) * envelope;
            stack.pushPose();
            stack.translate((float) Math.cos(angle) * radius, height, (float) Math.sin(angle) * radius);
            stack.rotate(Axis.YP.rotationDegrees(i * 17 + turn * 1.7F));
            box(stack.last(), vertices, 0, 0, 0, size, size, size,
                    alpha(i % 4 == 0 ? 0xFFB67AF0 : 0xFF39304E, envelope));
            stack.popPose();
        }
        stack.popPose();
    }

    private static boolean core(int x, int y, int z) { return x * x + y * y + z * z <= 11; }

    private static void voxelCore(PoseStack stack, VertexConsumer vertices) {
        for (int x = -3; x <= 3; x++) for (int y = -3; y <= 3; y++) for (int z = -3; z <= 3; z++) {
            if (!core(x, y, z)) continue;
            float a = x - .5F, b = x + .5F, c = y - .5F, d = y + .5F, e = z - .5F, f = z + .5F;
            int color = x + y + z > 3 ? 0xFF21162F : 0xFF100D17;
            if (!core(x - 1, y, z)) quad(stack.last(), vertices, a,c,f,a,c,e,a,d,e,a,d,f,shade(color,.8F));
            if (!core(x + 1, y, z)) quad(stack.last(), vertices, b,c,e,b,c,f,b,d,f,b,d,e,color);
            if (!core(x, y - 1, z)) quad(stack.last(), vertices, a,c,f,b,c,f,b,c,e,a,c,e,shade(color,.7F));
            if (!core(x, y + 1, z)) quad(stack.last(), vertices, a,d,e,b,d,e,b,d,f,a,d,f,color);
            if (!core(x, y, z - 1)) quad(stack.last(), vertices, a,c,e,b,c,e,b,d,e,a,d,e,color);
            if (!core(x, y, z + 1)) quad(stack.last(), vertices, b,c,f,a,c,f,a,d,f,b,d,f,shade(color,.75F));
        }
    }

    private static float random(long seed, int index) {
        long hash = seed + index * 0x9E3779B97F4A7C15L;
        hash = (hash ^ hash >>> 30) * 0xBF58476D1CE4E5B9L;
        hash = (hash ^ hash >>> 27) * 0x94D049BB133111EBL;
        return ((hash ^ hash >>> 31) >>> 40) / 16777216F;
    }
}
