package ru.orion.client.effect;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import ru.orion.client.config.OrionConfigManager;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * HitColor: при ударе по сущности подсвечивается именно её хитбокс — рамка по рёбрам
 * плюс полупрозрачная заливка граней. Цвет и прозрачность заливки настраиваются;
 * вспышка плавно затухает за заданное время.
 */
public final class OrionHitColorFlash {
    private static final float LINE_WIDTH = 2.5F;
    private static final float EDGE_INFLATE = 0.004F; // рамка чуть шире заливки, чтобы не мерцали друг об друга

    private final OrionConfigManager configManager;
    private final Map<UUID, Long> hitTimes = new HashMap<>();
    private final WeakHashMap<LivingEntityRenderState, Integer> modelTints = new WeakHashMap<>();
    private Object level;

    public OrionHitColorFlash(OrionConfigManager configManager) {
        this.configManager = configManager;
    }

    public void register() {
        LevelRenderEvents.COLLECT_SUBMITS.register(this::render);
    }

    /** Вызывается из MultiPlayerGameModeMixin при атаке. */
    public void recordHit(Entity target) {
        var config = configManager.getConfig();
        if (config.isHitColorEnabled() && (!config.isHitColorPlayersOnly() || target instanceof Player)) {
            if (level != Minecraft.getInstance().level) {
                hitTimes.clear();
                modelTints.clear();
                level = Minecraft.getInstance().level;
            }
            hitTimes.put(target.getUUID(), System.nanoTime());
        }
    }

    public void prepareModel(Entity entity, LivingEntityRenderState state) {
        modelTints.remove(state);
        var config = configManager.getConfig();
        if (!config.isHitColorEnabled() || config.isHitColorPlayersOnly() && !(entity instanceof Player)) return;
        state.hasRedOverlay = false;
        if (config.getHitColorMode() != 1 || Minecraft.getInstance().level != level) return;
        float fade = fade(entity.getUUID(), System.nanoTime());
        if (fade <= 0) return;
        int color = OrionRainbow.orElse(parseColor(config.getHitColorColor()));
        float strength = fade * config.getHitColorOpacity() / 100F;
        int tint = ARGB.srgbLerp(strength, -1, color);
        modelTints.put(state, tint);
    }

    public int modelTint(LivingEntityRenderState state, int original) {
        Integer tint = modelTints.get(state);
        return tint == null ? original : ARGB.multiply(original, tint);
    }

    private float fade(UUID id, long now) {
        Long hit = hitTimes.get(id);
        return hit == null ? 0 : Math.max(0, 1 - (now - hit) / (configManager.getConfig().getHitColorDuration() * 1_000_000F));
    }

    private void render(LevelRenderContext context) {
        if (hitTimes.isEmpty() || !configManager.getConfig().isHitColorEnabled()) {
            hitTimes.clear();
            return;
        }

        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.level != level) {
            hitTimes.clear();
            modelTints.clear();
            return;
        }

        long now = System.nanoTime();
        long lifetime = configManager.getConfig().getHitColorDuration() * 1_000_000L;
        hitTimes.values().removeIf(time -> now - time > lifetime);
        if (configManager.getConfig().getHitColorMode() == 1) return;
        if (hitTimes.isEmpty()) {
            return;
        }

        Vec3 cam = client.gameRenderer.mainCamera().position();
        float partialTick = client.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        int rgb = OrionRainbow.orElse(parseColor(configManager.getConfig().getHitColorColor()));
        float opacity = configManager.getConfig().getHitColorOpacity() / 100.0F;
        PoseStack poseStack = context.poseStack();

        for (Entity entity : client.level.entitiesForRendering()) {
            if (!hitTimes.containsKey(entity.getUUID())) {
                continue;
            }

            float fade = fade(entity.getUUID(), now);
            int fillAlpha = Math.round(255.0F * opacity * fade);
            if (fillAlpha <= 2) {
                continue;
            }
            int fillColor = ARGB.color(fillAlpha, ARGB.red(rgb), ARGB.green(rgb), ARGB.blue(rgb));
            int edgeColor = ARGB.color(Math.round(255.0F * fade), ARGB.red(rgb), ARGB.green(rgb), ARGB.blue(rgb));

            Vec3 lerped = entity.getPosition(partialTick);
            AABB box = entity.getBoundingBox().move(lerped.subtract(entity.position()));

            poseStack.pushPose();
            poseStack.translate(box.minX - cam.x, box.minY - cam.y, box.minZ - cam.z);
            float sx = (float) (box.maxX - box.minX);
            float sy = (float) (box.maxY - box.minY);
            float sz = (float) (box.maxZ - box.minZ);
            if (configManager.getConfig().getHitColorMode() == 0) context.submitNodeCollector().submitCustomGeometry(
                    poseStack,
                    RenderTypes.debugQuads(),
                    (pose, vertexConsumer) -> buildFilledBox(pose, vertexConsumer, sx, sy, sz, fillColor)
            );
            context.submitNodeCollector().submitCustomGeometry(
                    poseStack,
                    RenderTypes.lines(),
                    (pose, vertexConsumer) -> buildWireBox(pose, vertexConsumer, sx, sy, sz, edgeColor)
            );
            poseStack.popPose();
        }
    }

    /** 12 рёбер бокса (0,0,0)..(sx,sy,sz), чуть шире заливки. */
    private static void buildWireBox(PoseStack.Pose pose, VertexConsumer vc, float sx, float sy, float sz, int color) {
        float e = EDGE_INFLATE;
        // Нижняя грань.
        line(pose, vc, -e, -e, -e, sx + e, -e, -e, color);
        line(pose, vc, sx + e, -e, -e, sx + e, -e, sz + e, color);
        line(pose, vc, sx + e, -e, sz + e, -e, -e, sz + e, color);
        line(pose, vc, -e, -e, sz + e, -e, -e, -e, color);
        // Верхняя грань.
        line(pose, vc, -e, sy + e, -e, sx + e, sy + e, -e, color);
        line(pose, vc, sx + e, sy + e, -e, sx + e, sy + e, sz + e, color);
        line(pose, vc, sx + e, sy + e, sz + e, -e, sy + e, sz + e, color);
        line(pose, vc, -e, sy + e, sz + e, -e, sy + e, -e, color);
        // Вертикальные рёбра.
        line(pose, vc, -e, -e, -e, -e, sy + e, -e, color);
        line(pose, vc, sx + e, -e, -e, sx + e, sy + e, -e, color);
        line(pose, vc, sx + e, -e, sz + e, sx + e, sy + e, sz + e, color);
        line(pose, vc, -e, -e, sz + e, -e, sy + e, sz + e, color);
    }

    private static void line(PoseStack.Pose pose, VertexConsumer vc,
                             float x1, float y1, float z1, float x2, float y2, float z2, int color) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float dz = z2 - z1;
        float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 1.0E-4F) {
            return;
        }
        dx /= length;
        dy /= length;
        dz /= length;
        // Формат lines() в 26.x требует LineWidth в каждой вершине.
        vc.addVertex(pose, x1, y1, z1).setColor(color).setNormal(pose, dx, dy, dz).setLineWidth(LINE_WIDTH);
        vc.addVertex(pose, x2, y2, z2).setColor(color).setNormal(pose, dx, dy, dz).setLineWidth(LINE_WIDTH);
    }

    /** 6 граней бокса (0,0,0)..(sx,sy,sz). */
    private static void buildFilledBox(PoseStack.Pose pose, VertexConsumer vc, float sx, float sy, float sz, int color) {
        // Низ и верх.
        quad(pose, vc, color, 0, 0, 0, sx, 0, 0, sx, 0, sz, 0, 0, sz);
        quad(pose, vc, color, 0, sy, 0, 0, sy, sz, sx, sy, sz, sx, sy, 0);
        // Стороны по Z.
        quad(pose, vc, color, 0, 0, 0, 0, sy, 0, sx, sy, 0, sx, 0, 0);
        quad(pose, vc, color, 0, 0, sz, sx, 0, sz, sx, sy, sz, 0, sy, sz);
        // Стороны по X.
        quad(pose, vc, color, 0, 0, 0, 0, 0, sz, 0, sy, sz, 0, sy, 0);
        quad(pose, vc, color, sx, 0, 0, sx, sy, 0, sx, sy, sz, sx, 0, sz);
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer vc, int color,
                             float x1, float y1, float z1, float x2, float y2, float z2,
                             float x3, float y3, float z3, float x4, float y4, float z4) {
        vc.addVertex(pose, x1, y1, z1).setColor(color);
        vc.addVertex(pose, x2, y2, z2).setColor(color);
        vc.addVertex(pose, x3, y3, z3).setColor(color);
        vc.addVertex(pose, x4, y4, z4).setColor(color);
    }

    private static int parseColor(String hex) {
        try {
            return ARGB.opaque(Integer.parseInt(hex.substring(1), 16));
        } catch (RuntimeException ignored) {
            return ARGB.color(255, 239, 68, 68);
        }
    }
}
