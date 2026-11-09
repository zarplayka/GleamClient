package ru.orion.client.effect.kill;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import static ru.orion.client.effect.kill.OrionKillGeometry.alpha;
import static ru.orion.client.effect.kill.OrionKillGeometry.quad;
import static ru.orion.client.effect.kill.OrionKillGeometry.shade;
import static ru.orion.client.effect.kill.OrionKillGeometry.smooth;

/** Плоские пиксельные вспышки и спрайты. Поворот к камере задаёт владелец сцены. */
public final class OrionSoulKillModels {
    private static final float TAU = (float) (Math.PI * 2);
    private static final String[] SOUL = {
            "...1...", "..121..", ".12221.", ".12221.", "1222221", "1222221",
            "1202021", ".12221.", "..111..", "..1.1.."
    };
    private static final String[] SPARK = {"..2..", "..1..", "21112", "..1..", "..2.."};
    private static final String[] FLAKE = {
            "......2......", "..1...1...1..", "...1..1..1...", "....1.1.1....",
            ".1...111...1.", "..111222111..", "2111222221112", "..111222111..",
            ".1...111...1.", "....1.1.1....", "...1..1..1...", "..1...1...1..", "......2......"
    };
    private static final String[] PETAL = {".11..", "1221.", "12221", ".1221", "..11."};
    private static final String[] SHARD = {"..2..", ".121.", "12221", ".121.", "..1.."};

    private OrionSoulKillModels() { }

    public static float duration(int type) {
        return switch (type) {
            case 0, 5 -> 2F;
            case 1 -> 1.45F;
            case 3 -> 1.9F;
            case 6 -> 1.8F;
            case 8 -> 2.2F;
            default -> throw new IllegalArgumentException("Unknown pixel kill effect: " + type);
        };
    }

    public static void render(int type, PoseStack stack, VertexConsumer vertices, float seconds,
                              int quality, float particles, long seed) {
        float t = seconds / duration(type);
        if (t <= 0 || t >= 1) return;
        stack.pushPose();
        switch (type) {
            case 0 -> soulBloom(stack, vertices, t, quality, particles, seed);
            case 1 -> criticalBurst(stack, vertices, t, quality, particles, seed);
            case 3 -> enderRift(stack, vertices, t, quality, particles, seed);
            case 5 -> sculkEcho(stack, vertices, t, quality, particles, seed);
            case 6 -> frostSigil(stack, vertices, t, quality, particles, seed);
            case 8 -> cherryStorm(stack, vertices, t, quality, particles, seed);
            default -> throw new IllegalArgumentException("Unknown pixel kill effect: " + type);
        }
        stack.popPose();
    }

    private static void soulBloom(PoseStack s, VertexConsumer v, float t, int detail, float particles, long seed) {
        float fade = envelope(t);
        int count = 3 + (int) (particles * (5 + detail * 3));
        for (int i = 0; i < count; i++) {
            float delay = i == 0 ? 0 : random(seed, i) * .22F;
            float age = (t - delay) / (1 - delay);
            if (age <= 0) continue;
            float angle = i * 2.399963F;
            float spread = (i == 0 ? .12F : .35F + random(seed, i + 20) * .9F) * smooth(age * 2);
            float x = (float) Math.cos(angle) * spread + (float) Math.sin(age * 7 + i) * .08F;
            float y = .5F + age * (1.3F + random(seed, i + 40) * .6F);
            float size = (i == 0 ? .067F : .029F + random(seed, i + 60) * .015F) * (1 - age * .4F);
            sprite(s, v, SOUL, x, y, i % 3 * .006F, size, (float) Math.sin(age * 5 + i) * 9,
                    0xFF2BC5C9, 0xFFD4FFEF, fade * (1 - age * .3F));
        }
        sparks(s, v, t, particles, seed, detail, 0xFF64DAD3, .8F, .5F);
    }

    private static void criticalBurst(PoseStack s, VertexConsumer v, float t, int detail, float particles, long seed) {
        float flash = smooth(t / .07F) * (1 - smooth((t - .12F) / .45F));
        float scale = .13F * (1 + t);
        sprite(s, v, SPARK, 0, 1.05F, .04F, scale, 45, 0xFFFFBD47, 0xFFFFFFD3, flash);
        int count = 8 + (int) (particles * (10 + detail * 4));
        for (int i = 0; i < count; i++) {
            float angle = i * TAU / count + random(seed, i) * .14F;
            float age = Math.max(0, (t - i % 3 * .025F) / .9F);
            float distance = age * (1.0F + random(seed, i + 12) * 1.3F);
            float x = (float) Math.cos(angle) * distance;
            float y = 1.05F + (float) Math.sin(angle) * distance - age * age * .5F;
            float size = (.025F + random(seed, i + 36) * .021F) * (1 - age * .55F);
            sprite(s, v, SPARK, x, y, i % 4 * .008F, size, i * 41 + t * 110,
                    i % 2 == 0 ? 0xFFFFB84A : 0xFFFF7D3E, 0xFFFFF0BD, envelope(t));
        }
    }

    private static void enderRift(PoseStack s, VertexConsumer v, float t, int detail, float particles, long seed) {
        float opening = smooth(t / .22F) * (1 - smooth((t - .56F) / .44F));
        float fade = envelope(t);
        for (int row = 0; row < 18; row++) {
            float y = .2F + row * .105F;
            float taper = (float) Math.sin(Math.PI * (row + .5) / 18);
            float width = (.12F + taper * .42F) * opening;
            float x = (Math.round(random(seed, row) * 3) - 1.5F) * .045F * opening;
            rect(s.last(), v, x, y, 0, width + .12F * opening, .108F, alpha(0xFF7743BB, fade));
            rect(s.last(), v, x, y, .003F, width, .108F, alpha(0xFF171025, fade));
            if (row % 4 == 0) rect(s.last(), v, x + width / 2, y, .005F,
                    .055F * opening, .105F, alpha(0xFFD59DFF, fade));
        }
        int count = 8 + (int) (particles * (8 + detail * 4));
        for (int i = 0; i < count; i++) {
            float age = (random(seed, i + 90) + t * 1.4F) % 1;
            float angle = i * 2.399963F + age * 1.7F;
            float radius = (1 - age) * 1.35F * opening;
            sprite(s, v, SPARK, (float) Math.cos(angle) * radius,
                    1.1F + (float) Math.sin(angle) * radius * .8F, .016F,
                    .018F + .012F * (1 - age), i * 19, 0xFFAB66F0, 0xFFF0B8FF,
                    fade * (1 - age) * .9F);
        }
    }

    private static void sculkEcho(PoseStack s, VertexConsumer v, float t, int detail, float particles, long seed) {
        for (int wave = 0; wave < 3; wave++) {
            float age = (t - wave * .15F) / .7F;
            if (age <= 0 || age >= 1) continue;
            float radius = .17F + age * 1.25F;
            float fade = smooth(age / .08F) * (1 - age);
            pixelRing(s.last(), v, 0, 1.1F, wave * .006F, radius, alpha(0xFF42C9C8, fade));
            pixelRing(s.last(), v, 0, 1.1F, wave * .006F + .001F, radius * .92F, alpha(0xFFB5FFF0, fade * .6F));
        }
        sprite(s, v, SPARK, 0, 1.1F, .04F, .067F, 45, 0xFF1D9C9B, 0xFFDCFFF2,
                smooth(t / .05F) * (1 - smooth(t / .4F)));
        sparks(s, v, t, particles, seed, detail, 0xFF58C5CA, 1.35F, .9F);
    }

    private static void frostSigil(PoseStack s, VertexConsumer v, float t, int detail, float particles, long seed) {
        float bloom = smooth(t / .14F);
        float freeze = 1 - smooth((t - .32F) / .35F);
        sprite(s, v, FLAKE, 0, 1.1F, 0, .076F * bloom, 14 - t * 20,
                0xFF77CEEF, 0xFFF3FFFF, freeze * envelope(t));
        int count = 8 + (int) (particles * (12 + detail * 4));
        float burst = Math.max(0, (t - .22F) / .78F);
        for (int i = 0; i < count; i++) {
            float angle = i * TAU / count;
            float radius = .15F + burst * (1 + random(seed, i) * .9F);
            float x = (float) Math.cos(angle) * radius;
            float y = 1.1F + (float) Math.sin(angle) * radius - burst * burst * .6F;
            sprite(s, v, SHARD, x, y, .02F, (.023F + random(seed, i + 30) * .018F) * (1 - burst * .5F),
                    i * 31 + burst * 100, 0xFF82C9F1, 0xFFE7FFFF, smooth(burst / .06F) * (1 - burst));
        }
    }

    private static void cherryStorm(PoseStack s, VertexConsumer v, float t, int detail, float particles, long seed) {
        float fade = envelope(t);
        int count = 12 + (int) (particles * (12 + detail * 8));
        for (int i = 0; i < count; i++) {
            float angle = i * 2.399963F + t * 4.4F;
            float radius = (.24F + random(seed, i) * 1.1F) * smooth(t / .3F);
            float rise = random(seed, i + 30) * .7F + t * 1.4F;
            float x = (float) Math.cos(angle) * radius;
            float y = .15F + rise + (float) Math.sin(angle) * .17F;
            float scale = (.024F + random(seed, i + 60) * .022F) * (1 - t * .25F);
            sprite(s, v, PETAL, x, y, i % 4 * .007F, scale,
                    angle * 57.29578F + t * 50, i % 3 == 0 ? 0xFFE77EAF : 0xFFF3A9C7, 0xFFFFE6E6, fade);
        }
    }

    private static void sparks(PoseStack s, VertexConsumer v, float t, float particles, long seed, int detail,
                               int color, float radius, float lift) {
        int count = (int) (particles * (8 + detail * 4));
        for (int i = 0; i < count; i++) {
            float angle = i * 2.399963F;
            float distance = t * radius * (1 + random(seed, i));
            float size = .025F * (1 - t);
            rect(s.last(), v, (float) Math.cos(angle) * distance, lift + t * .8F - t * t * .6F,
                    .03F, size, size, alpha(color, envelope(t)));
        }
    }

    private static void pixelRing(PoseStack.Pose pose, VertexConsumer vertices, float x, float y, float z,
                                  float radius, int color) {
        float step = radius / 8;
        for (int row = -8; row <= 8; row++) {
            for (int column = -8; column <= 8; column++) {
                int distance = row * row + column * column;
                if (distance < 49 || distance > 64) continue;
                rect(pose, vertices, x + column * step, y + row * step, z, step * 1.03F, step * 1.03F, color);
            }
        }
    }

    private static void sprite(PoseStack s, VertexConsumer v, String[] pattern, float x, float y, float z,
                               float pixel, float degrees, int base, int light, float opacity) {
        if (opacity <= .005F || pixel <= .0001F) return;
        s.pushPose();
        s.translate(x, y, z);
        s.rotate(Axis.ZP.rotationDegrees(degrees));
        for (int row = 0; row < pattern.length; row++) {
            String line = pattern[row];
            for (int column = 0; column < line.length();) {
                char glyph = line.charAt(column);
                int end = column + 1;
                while (end < line.length() && line.charAt(end) == glyph) end++;
                if (glyph != '.') {
                    int color = glyph == '2' ? light : glyph == '0' ? shade(base, .32F) : base;
                    rect(s.last(), v, ((column + end) / 2F - line.length() / 2F) * pixel,
                            (pattern.length / 2F - row - .5F) * pixel, 0, (end - column) * pixel, pixel,
                            alpha(color, opacity));
                }
                column = end;
            }
        }
        s.popPose();
    }

    private static void rect(PoseStack.Pose pose, VertexConsumer v, float x, float y, float z, float w, float h, int color) {
        if ((color >>> 24) < 2 || w <= 0 || h <= 0) return;
        float left = x - w / 2, right = x + w / 2, bottom = y - h / 2, top = y + h / 2;
        quad(pose, v, left,bottom,z,right,bottom,z,right,top,z,left,top,z,color);
    }

    private static float envelope(float t) {
        return smooth(t / .06F) * (1 - smooth((t - .65F) / .35F));
    }

    private static float random(long seed, int index) {
        long hash = seed + index * 0x9E3779B97F4A7C15L;
        hash = (hash ^ hash >>> 30) * 0xBF58476D1CE4E5B9L;
        hash = (hash ^ hash >>> 27) * 0x94D049BB133111EBL;
        return ((hash ^ hash >>> 31) >>> 40) / 16777216F;
    }
}
