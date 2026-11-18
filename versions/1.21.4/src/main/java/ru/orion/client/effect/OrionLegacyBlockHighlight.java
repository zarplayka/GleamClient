package ru.orion.client.effect;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import ru.orion.client.config.OrionConfig;

public final class OrionLegacyBlockHighlight {
    private final OrionConfig config;
    private AABB box;
    private float opacity;
    private long lastTime;
    private ClientLevel level;
    private float previousLineWidth;
    private float previousGlowWidth;
    private RenderType lineLayer;
    private RenderType glowLayer;

    public OrionLegacyBlockHighlight(OrionConfig config) { this.config = config; }
    public void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(this::render);
        WorldRenderEvents.BEFORE_BLOCK_OUTLINE.register((context, hit) ->
                !config.isBlockHighlightEnabled() || !OrionWorldGeometry.isAvailable(context));
    }

    private void render(WorldRenderContext context) {
        Minecraft client = Minecraft.getInstance();
        if (client.level != level) {
            level = client.level;
            resetOutline();
        }
        if (!config.isBlockHighlightEnabled() || client.level == null || client.player == null
                || !OrionWorldGeometry.isAvailable(context)) {
            resetOutline();
            return;
        }
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
        Vec3 camera = context.camera().getPosition();
        AABB relative = box.move(-camera.x, -camera.y, -camera.z);
        int rgb = OrionWorldGeometry.color(config, OrionWorldGeometry.parseColor(config.getBlockHighlightColor()));
        int color = ARGB.color(Math.round(opacity * 255 * config.getBlockHighlightOpacity() / 100F), ARGB.red(rgb), ARGB.green(rgb), ARGB.blue(rgb));
        int glow = ARGB.color(Math.round(opacity * 255 * config.getBlockHighlightGlowOpacity() / 100F), ARGB.red(rgb), ARGB.green(rgb), ARGB.blue(rgb));
        boolean glowEnabled = config.isBlockHighlightGlow();
        float glowWidth = config.getBlockHighlightGlowWidth();
        float lineWidth = config.getBlockHighlightLineWidth();
        PoseStack poses = context.matrixStack();
        if (glowEnabled) {
            if (glowLayer == null || previousGlowWidth != glowWidth) {
                glowLayer = OrionEspRenderTypes.outline(glowWidth);
                previousGlowWidth = glowWidth;
            }
            OrionWorldGeometry.draw(context, poses, glowLayer, (pose, vc) -> drawBox(pose, vc, relative, glow));
        }
        if (lineLayer == null || previousLineWidth != lineWidth) {
            lineLayer = OrionEspRenderTypes.outline(lineWidth);
            previousLineWidth = lineWidth;
        }
        OrionWorldGeometry.draw(context, poses, lineLayer, (pose, vc) -> drawBox(pose, vc, relative, color));
    }
    private void resetOutline() {
        box = null;
        opacity = 0;
        lastTime = 0;
    }

    private static AABB blend(AABB a, AABB b, double t) {
        return new AABB(a.minX+(b.minX-a.minX)*t, a.minY+(b.minY-a.minY)*t, a.minZ+(b.minZ-a.minZ)*t,
                a.maxX+(b.maxX-a.maxX)*t, a.maxY+(b.maxY-a.maxY)*t, a.maxZ+(b.maxZ-a.maxZ)*t);
    }
    private static void drawBox(PoseStack.Pose pose, VertexConsumer vc, AABB b, int color) {
        for (int axis = 0; axis < 3; axis++) for (int i = 0; i < 4; i++) {
            float x = (float) ((i & 1) == 0 ? b.minX : b.maxX);
            float y = (float) ((i & 1) == 0 ? b.minY : b.maxY);
            float z = (float) ((i & 2) == 0 ? b.minZ : b.maxZ);
            if (axis == 0) line(pose, vc, (float)b.minX, y, z, (float)b.maxX, y, z, color);
            if (axis == 1) line(pose, vc, x, (float)b.minY, z, x, (float)b.maxY, z, color);
            if (axis == 2) line(pose, vc, x, (float)((i & 2)==0 ? b.minY : b.maxY), (float)b.minZ,
                    x, (float)((i & 2)==0 ? b.minY : b.maxY), (float)b.maxZ, color);
        }
    }
    private static void line(PoseStack.Pose p, VertexConsumer vc, float x, float y, float z,
                             float x2, float y2, float z2, int color) {
        float dx=x2-x, dy=y2-y, dz=z2-z, length=(float)Math.sqrt(dx*dx+dy*dy+dz*dz);
        if (length < 1.0E-5F) return;
        dx/=length; dy/=length; dz/=length;
        vc.addVertex(p,x,y,z).setColor(color).setNormal(p,dx,dy,dz);
        vc.addVertex(p,x2,y2,z2).setColor(color).setNormal(p,dx,dy,dz);
    }
}
