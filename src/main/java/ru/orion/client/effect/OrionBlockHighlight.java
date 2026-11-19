package ru.orion.client.effect;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.OrionEspRenderTypes;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import ru.orion.client.config.OrionConfig;
import ru.orion.client.config.OrionConfigManager;
import ru.orion.client.mixin.OrionVoxelShapeAccessor;

import java.util.ArrayList;
import java.util.List;

public final class OrionBlockHighlight {
    private final OrionConfigManager configManager;
    private VoxelShape shape;
    private List<Face> faces = List.of();
    private List<Edge> edges = List.of();
    private Target current, outgoing;
    private Vec3 slideFrom;
    private float transition = 1F;
    private float opacity;
    private long lastTime;
    private Object level;

    public OrionBlockHighlight(OrionConfigManager configManager) { this.configManager = configManager; }
    public void register() { LevelRenderEvents.COLLECT_SUBMITS.register(this::render); }

    private void render(LevelRenderContext context) {
        Minecraft client = Minecraft.getInstance();
        var config = configManager.getConfig();
        if (level != client.level || !config.isBlockHighlightEnabled()) {
            level = client.level;
            shape = null;
            current = outgoing = null;
            slideFrom = null;
            opacity = 0;
            lastTime = 0;
        }
        if (!config.isBlockHighlightEnabled() || client.level == null || client.player == null) return;
        long now = System.nanoTime();
        float dt = lastTime == 0 ? .016F : Math.clamp((now - lastTime) / 1_000_000_000F, 0F, .08F);
        lastTime = now;
        var outline = context.levelState().blockOutlineRenderState;
        int mode = config.getBlockHighlightMode();
        if (outline != null && !outline.shape().isEmpty() && client.hitResult instanceof BlockHitResult hit
                && hit.getType() == HitResult.Type.BLOCK && outline.pos().equals(hit.getBlockPos())) {
            boolean geometryChanged = !outline.shape().equals(shape);
            if (geometryChanged) bake(outline.shape());
            Vec3 origin = Vec3.atLowerCornerOf(outline.pos());
            Direction face = hit.getDirection();
            double plane = nearestPlane(face, hit.getLocation().subtract(origin));
            boolean moved = current != null && !outline.pos().equals(current.position);
            boolean faceChanged = current != null && (face != current.face || Math.abs(plane - current.plane) > 1.e-5);
            if (current == null || moved || geometryChanged || (mode == 3 && faceChanged)) {
                Vec3 displayed = current == null ? origin : outlinePosition();
                boolean animate = current != null && opacity > .01F;
                outgoing = animate ? current : null;
                slideFrom = animate && moved && !geometryChanged && displayed.distanceToSqr(origin) <= 9D ? displayed : null;
                transition = animate ? 0F : 1F;
                current = new Target(outline.pos().immutable(), origin, faces, edges, face, plane);
            } else if (faceChanged) {
                current = new Target(current.position, current.origin, current.faces, current.edges, face, plane);
            }
            opacity = Math.min(1F, opacity + dt * config.getBlockHighlightFadeSpeed());
        } else {
            opacity = Math.max(0F, opacity - dt * config.getBlockHighlightFadeSpeed());
        }
        transition = Math.min(1F, transition + dt * config.getBlockHighlightMoveSpeed() * .45F);
        if (transition >= 1F) {
            outgoing = null;
            slideFrom = null;
        }
        if (current == null || opacity <= 0F) return;
        var camera = context.levelState().cameraRenderState;
        if (camera == null || camera.pos == null) return;
        int rgb = OrionRainbow.orElse(OrionWorldChanger.parseOpaqueColor(config.getBlockHighlightColor()));
        float weight = outgoing == null ? 1F : easedTransition();
        if (mode != 0) {
            // The fill stays on the real block faces throughout the transition.
            if (outgoing != null) drawFill(context, camera.pos, outgoing, mode, rgb, opacity * (1 - weight), config);
            drawFill(context, camera.pos, current, mode, rgb, opacity * weight, config);
        }
        if (mode == 0 || mode == 2) {
            if (outgoing != null && slideFrom == null) {
                drawOutline(context, camera.pos, outgoing, outgoing.origin, rgb, opacity * (1 - weight), config);
            }
            drawOutline(context, camera.pos, current, outlinePosition(), rgb,
                    opacity * (slideFrom == null ? weight : 1F), config);
        }
    }

    private float easedTransition() {
        float remaining = 1F - transition;
        return 1F - remaining * remaining * remaining;
    }

    private Vec3 outlinePosition() {
        return slideFrom == null ? current.origin : slideFrom.lerp(current.origin, easedTransition());
    }

    private static void drawFill(LevelRenderContext context, Vec3 camera, Target target, int mode, int rgb,
                                 float strength, OrionConfig config) {
        int alpha = Math.round(strength * 255 * config.getBlockHighlightFillOpacity() / 100F);
        if (alpha < 2) return;
        int color = ARGB.color(alpha, rgb);
        double bias = Math.max(.0015, config.getBlockHighlightOffset());
        Vec3 offset = target.origin.subtract(camera);
        PoseStack stack = context.poseStack();
        stack.pushPose();
        stack.translate(offset.x, offset.y, offset.z);
        context.submitNodeCollector().submitCustomGeometry(stack, OrionEspRenderTypes.translucentColorQuadsDepthTested(),
                (pose, consumer) -> {
                    for (Face face : target.faces) {
                        if (mode != 3 || (face.direction == target.face && Math.abs(face.plane() - target.plane) < 1.e-5)) {
                            face.draw(pose, consumer, color, bias);
                        }
                    }
                });
        stack.popPose();
    }

    private static void drawOutline(LevelRenderContext context, Vec3 camera, Target target, Vec3 anchor, int rgb,
                                    float strength, OrionConfig config) {
        int alpha = Math.round(strength * 255 * config.getBlockHighlightOpacity() / 100F);
        if (alpha < 2) return;
        int color = ARGB.color(alpha, rgb);
        int glow = ARGB.color(Math.round(strength * 255 * config.getBlockHighlightGlowOpacity() / 100F), rgb);
        float width = config.getBlockHighlightLineWidth();
        float glowWidth = config.getBlockHighlightGlowWidth();
        boolean glowing = config.isBlockHighlightGlow();
        Vec3 offset = anchor.subtract(camera);
        PoseStack stack = context.poseStack();
        stack.pushPose();
        stack.translate(offset.x, offset.y, offset.z);
        context.submitNodeCollector().submitCustomGeometry(stack, RenderTypes.lines(), (pose, consumer) -> {
            for (Edge edge : target.edges) {
                if (glowing) edge.draw(pose, consumer, glow, glowWidth);
                edge.draw(pose, consumer, color, width);
            }
        });
        stack.popPose();
    }

    private record Target(BlockPos position, Vec3 origin, List<Face> faces, List<Edge> edges, Direction face, double plane) {}

    private void bake(VoxelShape voxel) {
        shape = voxel;
        List<Face> nextFaces = new ArrayList<>();
        var x = voxel.getCoords(Direction.Axis.X);
        var y = voxel.getCoords(Direction.Axis.Y);
        var z = voxel.getCoords(Direction.Axis.Z);
        ((OrionVoxelShapeAccessor) voxel).gleam$discreteShape().forAllFaces((normal, i, j, k) ->
                nextFaces.add(new Face(normal, x.getDouble(i), y.getDouble(j), z.getDouble(k),
                        x.getDouble(i + 1), y.getDouble(j + 1), z.getDouble(k + 1))));
        List<Edge> nextEdges = new ArrayList<>();
        voxel.forAllEdges((x1, y1, z1, x2, y2, z2) ->
                nextEdges.add(new Edge((float) x1, (float) y1, (float) z1, (float) x2, (float) y2, (float) z2)));
        faces = List.copyOf(nextFaces);
        edges = List.copyOf(nextEdges);
    }

    private double nearestPlane(Direction direction, Vec3 hit) {
        Direction.Axis axis = direction.getAxis();
        double best = Double.POSITIVE_INFINITY;
        double result = hit.get(axis);
        for (Face face : faces) {
            if (face.direction != direction) continue;
            double distance = face.distanceTo(hit);
            if (distance < best) {
                best = distance;
                result = face.plane();
            }
        }
        return result;
    }

    private record Face(Direction direction, double x, double y, double z, double x2, double y2, double z2) {
        double plane() {
            return switch (direction) {
                case DOWN -> y;
                case UP -> y2;
                case NORTH -> z;
                case SOUTH -> z2;
                case WEST -> x;
                case EAST -> x2;
            };
        }

        double distanceTo(Vec3 point) {
            double dx = point.x - Mth.clamp(point.x, x, x2);
            double dy = point.y - Mth.clamp(point.y, y, y2);
            double dz = point.z - Mth.clamp(point.z, z, z2);
            return dx * dx + dy * dy + dz * dz + Mth.square(point.get(direction.getAxis()) - plane());
        }

        void draw(PoseStack.Pose pose, VertexConsumer consumer, int color, double bias) {
            double wx = x - bias, ex = x2 + bias, ly = y - bias, hy = y2 + bias, nz = z - bias, sz = z2 + bias;
            switch (direction) {
                case DOWN -> quad(pose, consumer, color, x, ly, z, x2, ly, z, x2, ly, z2, x, ly, z2);
                case UP -> quad(pose, consumer, color, x, hy, z2, x2, hy, z2, x2, hy, z, x, hy, z);
                case NORTH -> quad(pose, consumer, color, x, y, nz, x, y2, nz, x2, y2, nz, x2, y, nz);
                case SOUTH -> quad(pose, consumer, color, x2, y, sz, x2, y2, sz, x, y2, sz, x, y, sz);
                case WEST -> quad(pose, consumer, color, wx, y, z2, wx, y2, z2, wx, y2, z, wx, y, z);
                case EAST -> quad(pose, consumer, color, ex, y, z, ex, y2, z, ex, y2, z2, ex, y, z2);
            }
        }
    }

    private record Edge(float x, float y, float z, float x2, float y2, float z2) {
        void draw(PoseStack.Pose pose, VertexConsumer consumer, int color, float width) {
            float dx = x2 - x, dy = y2 - y, dz = z2 - z;
            float length = Mth.sqrt(dx * dx + dy * dy + dz * dz);
            if (length < 1.e-5F) return;
            consumer.addVertex(pose, x, y, z).setColor(color).setNormal(pose, dx / length, dy / length, dz / length).setLineWidth(width);
            consumer.addVertex(pose, x2, y2, z2).setColor(color).setNormal(pose, dx / length, dy / length, dz / length).setLineWidth(width);
        }
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer consumer, int color,
                             double x1, double y1, double z1, double x2, double y2, double z2,
                             double x3, double y3, double z3, double x4, double y4, double z4) {
        consumer.addVertex(pose, (float) x1, (float) y1, (float) z1).setColor(color);
        consumer.addVertex(pose, (float) x2, (float) y2, (float) z2).setColor(color);
        consumer.addVertex(pose, (float) x3, (float) y3, (float) z3).setColor(color);
        consumer.addVertex(pose, (float) x4, (float) y4, (float) z4).setColor(color);
    }
}
