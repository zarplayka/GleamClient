package ru.orion.client.effect;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import ru.orion.client.config.OrionConfigManager;

public final class OrionBlockHighlight {
    private final OrionConfigManager configManager;
    private AABB box;
    private float opacity;
    private long lastTime;

    public OrionBlockHighlight(OrionConfigManager configManager) { this.configManager = configManager; }
    public void register() { LevelRenderEvents.COLLECT_SUBMITS.register(this::render); }

    private void render(LevelRenderContext context) {
        Minecraft client = Minecraft.getInstance();
        if (!configManager.getConfig().isBlockHighlightEnabled() || client.level == null || client.player == null) {
            box = null; opacity = 0; lastTime = 0; return;
        }
        var config = configManager.getConfig();
        long now = System.nanoTime();
        float dt = lastTime == 0 ? 0 : Math.min(0.1F, (now - lastTime) / 1_000_000_000F);
        lastTime = now;
        AABB target = null;
        if (client.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
            var pos = hit.getBlockPos();
            var shape = client.level.getBlockState(pos).getShape(client.level, pos);
            if (!shape.isEmpty()) target = shape.bounds().move(pos).inflate(config.getBlockHighlightOffset());
        }
        opacity += ((target == null ? 0 : 1) - opacity) * (1F - (float) Math.exp(-config.getBlockHighlightFadeSpeed() * dt));
        if (target != null) {
            if (box == null || box.getCenter().distanceToSqr(target.getCenter()) > 64) box = target;
            else box = blend(box, target, 1.0 - Math.exp(-config.getBlockHighlightMoveSpeed() * dt));
        }
        if (box == null || opacity < 0.01F) return;
        Vec3 camera = client.gameRenderer.mainCamera().position();
        AABB relative = box.move(-camera.x, -camera.y, -camera.z);
        int rgb = OrionRainbow.orElse(OrionWorldChanger.parseOpaqueColor(configManager.getConfig().getBlockHighlightColor()));
        int color = ARGB.color(Math.round(opacity * 255 * config.getBlockHighlightOpacity() / 100F), ARGB.red(rgb), ARGB.green(rgb), ARGB.blue(rgb));
        int glow = ARGB.color(Math.round(opacity * 255 * config.getBlockHighlightGlowOpacity() / 100F), ARGB.red(rgb), ARGB.green(rgb), ARGB.blue(rgb));
        boolean glowEnabled = config.isBlockHighlightGlow();
        float glowWidth = config.getBlockHighlightGlowWidth();
        float lineWidth = config.getBlockHighlightLineWidth();
        context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.lines(), (pose, vc) -> {
            if (glowEnabled) drawBox(pose, vc, relative, glow, glowWidth);
            drawBox(pose, vc, relative, color, lineWidth);
        });
    }
    private static AABB blend(AABB a, AABB b, double t) {
        return new AABB(a.minX+(b.minX-a.minX)*t, a.minY+(b.minY-a.minY)*t, a.minZ+(b.minZ-a.minZ)*t,
                a.maxX+(b.maxX-a.maxX)*t, a.maxY+(b.maxY-a.maxY)*t, a.maxZ+(b.maxZ-a.maxZ)*t);
    }
    private static void drawBox(PoseStack.Pose pose, VertexConsumer vc, AABB b, int color, float width) {
        for (int axis = 0; axis < 3; axis++) for (int i = 0; i < 4; i++) {
            float x = (float) ((i & 1) == 0 ? b.minX : b.maxX);
            float y = (float) ((i & 1) == 0 ? b.minY : b.maxY);
            float z = (float) ((i & 2) == 0 ? b.minZ : b.maxZ);
            if (axis == 0) line(pose, vc, (float)b.minX, y, z, (float)b.maxX, y, z, color, width);
            if (axis == 1) line(pose, vc, x, (float)b.minY, z, x, (float)b.maxY, z, color, width);
            if (axis == 2) line(pose, vc, x, (float)((i & 2)==0 ? b.minY : b.maxY), (float)b.minZ,
                    x, (float)((i & 2)==0 ? b.minY : b.maxY), (float)b.maxZ, color, width);
        }
    }
    private static void line(PoseStack.Pose p, VertexConsumer vc, float x, float y, float z,
                             float x2, float y2, float z2, int color, float width) {
        float dx=x2-x, dy=y2-y, dz=z2-z, length=(float)Math.sqrt(dx*dx+dy*dy+dz*dz);
        if (length < 1.0E-5F) return;
        dx/=length; dy/=length; dz/=length;
        vc.addVertex(p,x,y,z).setColor(color).setNormal(p,dx,dy,dz).setLineWidth(width);
        vc.addVertex(p,x2,y2,z2).setColor(color).setNormal(p,dx,dy,dz).setLineWidth(width);
    }
}
