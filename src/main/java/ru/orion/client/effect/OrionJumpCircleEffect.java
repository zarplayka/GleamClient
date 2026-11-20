package ru.orion.client.effect;

import ru.orion.client.GleamMinecraft;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.rendertype.OrionEspRenderTypes;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;
import ru.orion.client.config.OrionConfigManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Spawns an expanding ring on the ground the moment the player jumps and renders it in world space.
 * Reimplemented for the MC 26.3 submit/extraction pipeline (no external code or assets).
 */
public final class OrionJumpCircleEffect {
    public static final String[] STYLES = {"Кольцо", "Рябь", "Руны", "Всплеск"};
    private static final int RIPPLES = 1, RUNES = 2, BURST = 3;
    private static final double JUMP_VELOCITY_THRESHOLD = 0.08; // tells a jump apart from walking off a ledge
    private static final float RING_DURATION = 0.60F;
    private static final float RIPPLE_DELAY = 0.11F;
    private static final float MIN_RADIUS = 0.15F;
    private static final float MAX_RADIUS = .78F;
    private static final int SEGMENTS = 48;
    private static final int BASE_ALPHA = 210;
    private static final int MAX_ACTIVE_CIRCLES = 16;
    private static final int FALLBACK_ACCENT = ARGB.color(255, 255, 124, 0); // #FF7C00

    private final OrionConfigManager configManager;
    private final List<Circle> circles = new ArrayList<>();
    private ClientLevel level;
    private boolean wasOnGround = true;
    private Vec3 lastGroundPosition;

    public OrionJumpCircleEffect(OrionConfigManager configManager) {
        this.configManager = configManager;
    }

    public void register() {
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        LevelRenderEvents.COLLECT_SUBMITS.register(this::collectSubmits);
    }

    private void tick(Minecraft client) {
        LocalPlayer player = client.player;
        if (client.level != level) {
            level = client.level;
            wasOnGround = true;
            lastGroundPosition = null;
            circles.clear();
        }
        var config = configManager.getConfig();
        if (player == null || !config.isJumpCircleEnabled()) {
            wasOnGround = true;
            lastGroundPosition = null;
            circles.clear();
            return;
        }

        long now = System.nanoTime();
        circles.removeIf(circle -> circle.age(now) >= circle.duration());

        boolean onGround = player.onGround();

        if (onGround) {
            lastGroundPosition = player.position();
        }

        if (wasOnGround && !onGround && lastGroundPosition != null
                && player.getDeltaMovement().y > JUMP_VELOCITY_THRESHOLD) {
            if (circles.size() >= MAX_ACTIVE_CIRCLES) {
                circles.remove(0);
            }
            circles.add(new Circle(lastGroundPosition.add(0, .025, 0), config.getJumpCircleStyle(), now));
        }

        wasOnGround = onGround;
    }

    private void collectSubmits(LevelRenderContext context) {
        Minecraft client = Minecraft.getInstance();
        var config = configManager.getConfig();
        if (circles.isEmpty() || client.level != level || !config.isJumpCircleEnabled()) return;
        Vec3 cam = GleamMinecraft.camera(client).position();
        PoseStack poseStack = context.poseStack();
        var renderType = OrionEspRenderTypes.translucentColorQuadsDepthTested();
        int rgb = OrionRainbow.orElse(parseColor(config.getJumpCircleColor()));
        float size = config.getJumpCircleSize();

        long now = System.nanoTime();
        for (Circle circle : circles) {
            float age = circle.age(now);
            if (age >= circle.duration()) continue;
            Vec3 origin = circle.origin().subtract(cam);
            poseStack.pushPose();
            poseStack.translate(origin.x, origin.y, origin.z);
            context.submitNodeCollector().submitCustomGeometry(
                    poseStack,
                    renderType,
                    (pose, vertexConsumer) -> draw(pose, vertexConsumer, circle, age, size, rgb)
            );

            poseStack.popPose();
        }
    }

    private static void draw(PoseStack.Pose pose, VertexConsumer vc, Circle circle, float age, float size, int rgb) {
        float progress = age / circle.duration();
        float radius = (MIN_RADIUS + (MAX_RADIUS - MIN_RADIUS) * easeOutCubic(progress)) * size;
        float alpha = BASE_ALPHA * fade(progress);
        int color = tint(rgb, alpha);
        switch (circle.style()) {
            case RIPPLES -> {
                for (int i = 0; i < 3; i++) {
                    float t = (age - i * RIPPLE_DELAY) / RING_DURATION;
                    if (t <= 0 || t >= 1) continue;
                    float edge = (.12F + .89F * easeOutCubic(t)) * size;
                    float width = (.031F - .011F * t) * size;
                    glowRing(pose, vc, edge, width, rgb, (210F - 38 * i) * fade(t));
                }
            }
            case RUNES -> {
                glowRing(pose, vc, radius * .74F, size * .022F, rgb, alpha);
                for (int i = 0; i < 8; i++) {
                    double angle = progress * .48 + i * Math.TAU / 8;
                    band(pose, vc, radius * .97F, radius, angle - .23, .46, 6, color, color);
                    float mark = radius * .88F;
                    diamond(pose, vc, mark, radius * .075F, radius * .018F, angle, color);
                    if ((i & 1) == 0) {
                        band(pose, vc, mark - size * .009F, mark + size * .009F, angle - .071, .142, 3, color, color);
                    }
                }
            }
            case BURST -> {
                float distance = (.11F + .84F * easeOutCubic(progress)) * size;
                glowRing(pose, vc, distance, size * (.05F - .024F * progress), rgb, alpha);
                for (int i = 0; i < 12; i++) {
                    double angle = i * Math.TAU / 12 + (i % 3 - 1) * .10;
                    float tip = distance + (.065F + i % 3 * .044F) * size * progress;
                    float length = (.055F + i % 2 * .025F) * (1F - progress * .65F) * size;
                    diamond(pose, vc, tip, length, .014F * size * (1F - progress * .6F),
                            angle, tint(rgb, alpha * (1F - progress * .38F)));
                }
            }
            default -> glowRing(pose, vc, radius, size * .034F, rgb, alpha);
        }
    }

    private static void glowRing(PoseStack.Pose pose, VertexConsumer vc, float radius, float width, int rgb, float alpha) {
        int transparent = tint(rgb, 0);
        int center = tint(rgb, alpha);
        float inner = radius - width * .45F;
        float outer = radius + width * .45F;
        band(pose, vc, Math.max(0, radius - width * 1.85F), inner, 0, Math.TAU, SEGMENTS, transparent, center);
        band(pose, vc, inner, outer, 0, Math.TAU, SEGMENTS, center, center);
        band(pose, vc, outer, radius + width * 1.85F, 0, Math.TAU, SEGMENTS, center, transparent);
    }

    private static void band(PoseStack.Pose pose, VertexConsumer vc, float inner, float outer,
                             double start, double arc, int segments, int innerColor, int outerColor) {
        for (int i = 0; i < segments; i++) {
            double a0 = start + arc * i / segments;
            double a1 = start + arc * (i + 1) / segments;
            float cos0 = (float) Math.cos(a0);
            float sin0 = (float) Math.sin(a0);
            float cos1 = (float) Math.cos(a1);
            float sin1 = (float) Math.sin(a1);

            // Flat quad on the XZ plane (local y = 0): innerA -> outerA -> outerB -> innerB.
            vc.addVertex(pose, inner * cos0, 0.0F, inner * sin0).setColor(innerColor);
            vc.addVertex(pose, outer * cos0, 0.0F, outer * sin0).setColor(outerColor);
            vc.addVertex(pose, outer * cos1, 0.0F, outer * sin1).setColor(outerColor);
            vc.addVertex(pose, inner * cos1, 0.0F, inner * sin1).setColor(innerColor);
        }
    }

    private static void diamond(PoseStack.Pose pose, VertexConsumer vc, float distance, float length,
                                float width, double angle, int color) {
        float x = (float) Math.cos(angle), z = (float) Math.sin(angle);
        vc.addVertex(pose, x * (distance - length), 0, z * (distance - length)).setColor(color);
        vc.addVertex(pose, x * distance + z * width, 0, z * distance - x * width).setColor(color);
        vc.addVertex(pose, x * (distance + length), 0, z * (distance + length)).setColor(color);
        vc.addVertex(pose, x * distance - z * width, 0, z * distance + x * width).setColor(color);
    }

    private static int tint(int rgb, float alpha) {
        return ARGB.color(Math.round(alpha), ARGB.red(rgb), ARGB.green(rgb), ARGB.blue(rgb));
    }

    private static float easeOutCubic(float t) {
        float f = 1.0F - t;
        return 1.0F - f * f * f;
    }

    /** Quick fade-in, hold, then fade-out across the lifetime. */
    private static float fade(float progress) {
        if (progress < 0.12F) {
            return progress / 0.12F;
        }

        if (progress > 0.55F) {
            return Math.max(0.0F, 1.0F - (progress - 0.55F) / 0.45F);
        }

        return 1.0F;
    }

    private static int parseColor(String color) {
        try {
            int rgb = Integer.parseUnsignedInt(color.substring(1), 16);
            return ARGB.color(255, rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255);
        } catch (RuntimeException exception) {
            return FALLBACK_ACCENT;
        }
    }

    private record Circle(Vec3 origin, int style, long spawnNanos) {
        private float duration() {
            return switch (style) {
                case RIPPLES -> RING_DURATION + RIPPLE_DELAY * 2;
                case RUNES -> .79F;
                case BURST -> .46F;
                default -> RING_DURATION;
            };
        }

        private float age(long now) {
            return (now - spawnNanos) / 1_000_000_000.0F;
        }
    }
}
