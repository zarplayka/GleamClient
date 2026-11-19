package ru.orion.client.effect;

import ru.orion.client.GleamMinecraft;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.OrionEspRenderTypes;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import ru.orion.client.config.OrionConfigManager;

public final class OrionBlockHighlight {
    private final OrionConfigManager configManager;
    private AABB box;
    private Direction face;
    private int mode = -1;
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
        int style = config.getBlockHighlightMode();
        if (mode != style) {
            box = null;
            opacity = 0;
            mode = style;
        }
        long now = System.nanoTime();
        float dt = lastTime == 0 ? 0 : Math.min(0.1F, (now - lastTime) / 1_000_000_000F);
        lastTime = now;
        AABB target = null;
        if (client.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
            var pos = hit.getBlockPos();
            var shape = client.level.getBlockState(pos).getShape(client.level, pos, CollisionContext.of(client.player));
            if (!shape.isEmpty()) {
                AABB bounds = shape.bounds();
                if (style == 3) {
                    Vec3 contact = hit.getLocation().subtract(Vec3.atLowerCornerOf(pos))
                            .relative(hit.getDirection(), -0.0001);
                    for (AABB part : shape.toAabbs()) {
                        if (part.inflate(0.0002).contains(contact)) {
                            bounds = part;
                            break;
                        }
                    }
                    if (face != hit.getDirection()) box = null;
                    face = hit.getDirection();
                }
                target = bounds.move(pos).inflate(config.getBlockHighlightOffset() + (style == 0 ? 0 : .0005));
            }
        }
        opacity += ((target == null ? 0 : 1) - opacity) * (1F - (float) Math.exp(-config.getBlockHighlightFadeSpeed() * dt));
        if (target != null) {
            if (box == null || box.getCenter().distanceToSqr(target.getCenter()) > 64) box = target;
            else box = blend(box, target, 1.0 - Math.exp(-config.getBlockHighlightMoveSpeed() * dt));
        }
        if (box == null || opacity < 0.01F) return;
        Vec3 camera = GleamMinecraft.camera(client).position();
        AABB relative = box.move(-camera.x, -camera.y, -camera.z);
        int rgb = OrionRainbow.orElse(OrionWorldChanger.parseOpaqueColor(config.getBlockHighlightColor()));
        if (style != 0) {
            int fill = ARGB.color(Math.round(opacity * 255 * config.getBlockHighlightFillOpacity() / 100F), rgb);
            Direction selectedFace = style == 3 ? face : null;
            context.submitNodeCollector().submitCustomGeometry(context.poseStack(), OrionEspRenderTypes.colorQuadsDepthTested(),
                    (pose, vc) -> drawFaces(pose, vc, relative, selectedFace, fill));
        }
        if (style == 1 || style == 3) return;
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

    private static void drawFaces(PoseStack.Pose pose, VertexConsumer vc, AABB box, Direction face, int color) {
        float x = (float) box.minX, y = (float) box.minY, z = (float) box.minZ;
        float maxX = (float) box.maxX, maxY = (float) box.maxY, maxZ = (float) box.maxZ;
        if (face == null || face == Direction.DOWN)
            quad(pose, vc, color, x, y, z, maxX, y, z, maxX, y, maxZ, x, y, maxZ);
        if (face == null || face == Direction.UP)
            quad(pose, vc, color, x, maxY, maxZ, maxX, maxY, maxZ, maxX, maxY, z, x, maxY, z);
        if (face == null || face == Direction.NORTH)
            quad(pose, vc, color, x, y, z, x, maxY, z, maxX, maxY, z, maxX, y, z);
        if (face == null || face == Direction.SOUTH)
            quad(pose, vc, color, maxX, y, maxZ, maxX, maxY, maxZ, x, maxY, maxZ, x, y, maxZ);
        if (face == null || face == Direction.WEST)
            quad(pose, vc, color, x, y, maxZ, x, maxY, maxZ, x, maxY, z, x, y, z);
        if (face == null || face == Direction.EAST)
            quad(pose, vc, color, maxX, y, z, maxX, maxY, z, maxX, maxY, maxZ, maxX, y, maxZ);
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer vc, int color,
                             float x1, float y1, float z1, float x2, float y2, float z2,
                             float x3, float y3, float z3, float x4, float y4, float z4) {
        vc.addVertex(pose, x1, y1, z1).setColor(color);
        vc.addVertex(pose, x2, y2, z2).setColor(color);
        vc.addVertex(pose, x3, y3, z3).setColor(color);
        vc.addVertex(pose, x4, y4, z4).setColor(color);
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
