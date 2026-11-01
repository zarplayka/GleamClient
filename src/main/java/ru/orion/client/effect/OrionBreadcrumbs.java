package ru.orion.client.effect;

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
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import ru.orion.client.config.OrionConfig;
import ru.orion.client.config.OrionConfigManager;

import java.util.ArrayDeque;
import java.util.List;
import java.util.ArrayList;

/** След сохраняет точки только во время движения и постепенно затухает. */
public final class OrionBreadcrumbs {
    private static final int MAX_POINTS = 200;
    private final OrionConfigManager configManager;
    private final ArrayDeque<Point> points = new ArrayDeque<>();
    private ClientLevel level;
    private LocalPlayer player;
    private Vec3 lastPosition;

    public OrionBreadcrumbs(OrionConfigManager configManager) { this.configManager = configManager; }

    public void register() {
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        LevelRenderEvents.COLLECT_SUBMITS.register(this::render);
    }

    private void clear() {
        points.clear();
        lastPosition = null;
        level = null;
        player = null;
    }

    private void tick(Minecraft client) {
        OrionConfig config = configManager.getConfig();
        if (!config.isBreadcrumbsEnabled() || client.player == null || client.level == null) {
            clear();
            return;
        }
        if (client.level != level || client.player != player) {
            clear();
            level = client.level;
            player = client.player;
        }
        long now = System.nanoTime();
        long lifetime = (long) (config.getBreadcrumbsDuration() * 1_000_000_000L);
        while (!points.isEmpty() && now - points.peekFirst().time() >= lifetime) points.removeFirst();
        Vec3 position = player.position().add(0.0D, 0.12D, 0.0D);
        if (lastPosition == null) {
            lastPosition = position;
            return;
        }
        double distance = position.distanceToSqr(lastPosition);
        if (distance > 64.0D) {
            points.clear();
        } else if (distance > 0.0004D) {
            if (points.isEmpty()) points.addLast(new Point(lastPosition, now));
            points.addLast(new Point(position, now));
            while (points.size() > MAX_POINTS) points.removeFirst();
        }
        lastPosition = position;
    }

    private void render(LevelRenderContext context) {
        OrionConfig config = configManager.getConfig();
        if (!config.isBreadcrumbsEnabled() || points.size() < 2) return;
        Vec3 camera = Minecraft.getInstance().gameRenderer.mainCamera().position();
        List<Point> snapshot = new ArrayList<>(points);
        LocalPlayer currentPlayer = Minecraft.getInstance().player;
        Point last = snapshot.get(snapshot.size() - 1);
        if (currentPlayer == player && last.position().distanceToSqr(currentPlayer.position().add(0, 0.12D, 0)) < 1.0E-6D) {
            float partial = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);
            Vec3 interpolated = new Vec3(Mth.lerp(partial, currentPlayer.xOld, currentPlayer.getX()),
                    Mth.lerp(partial, currentPlayer.yOld, currentPlayer.getY()) + 0.12D,
                    Mth.lerp(partial, currentPlayer.zOld, currentPlayer.getZ()));
            snapshot.set(snapshot.size() - 1, new Point(interpolated, last.time()));
        }
        long now = System.nanoTime();
        float duration = config.getBreadcrumbsDuration();
        float width = config.getBreadcrumbsWidth();
        float opacity = config.getBreadcrumbsOpacity() / 100.0F;
        boolean glow = config.isBreadcrumbsGlow();
        int rgb = OrionRainbow.orElse(parseColor(config.isBreadcrumbsUseAccent() ? config.getAccentColor() : config.getBreadcrumbsColor()));
        PoseStack stack = context.poseStack();
        stack.pushPose();
        context.submitNodeCollector().submitCustomGeometry(stack,
                config.isBreadcrumbsThroughWalls() ? OrionEspRenderTypes.colorQuads() : OrionEspRenderTypes.colorQuadsDepthTested(),
                (pose, vertices) -> drawTrail(pose, vertices, snapshot, camera, now, duration, width, opacity, glow, rgb));
        stack.popPose();
    }

    private static void drawTrail(PoseStack.Pose pose, VertexConsumer vertices, List<Point> points, Vec3 camera,
                                  long now, float duration, float width, float opacity, boolean glow, int rgb) {
        List<OrionRibbon.Point> path = new ArrayList<>(points.size());
        for (int i = 0; i < points.size(); i++) {
            Point point = points.get(i);
            float tail = Math.min(1.0F, i / 3.0F);
            float alpha = fade(point, now, duration) * opacity * tail;
            path.add(new OrionRibbon.Point(point.position().subtract(camera), color(rgb, alpha)));
        }
        OrionRibbon.draw(pose, vertices, OrionRibbon.smooth(path, 4), Vec3.ZERO, width, glow);
    }

    private static float fade(Point point, long now, float duration) {
        return Math.max(0.0F, 1.0F - (now - point.time()) / (duration * 1_000_000_000.0F));
    }
    private static int color(int rgb, float alpha) {
        return ARGB.color(Math.round(Math.max(0, Math.min(1, alpha)) * 255), ARGB.red(rgb), ARGB.green(rgb), ARGB.blue(rgb));
    }
    static int parseColor(String color) {
        try { return ARGB.opaque(Integer.parseInt(color.substring(1), 16)); }
        catch (RuntimeException ignored) { return ARGB.opaque(0xFF7C00); }
    }
    private record Point(Vec3 position, long time) { }
}
