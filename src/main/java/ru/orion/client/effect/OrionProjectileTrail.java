package ru.orion.client.effect;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.OrionEspRenderTypes;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.phys.Vec3;
import ru.orion.client.config.OrionConfig;
import ru.orion.client.config.OrionConfigManager;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Ограниченные по времени следы стрел, трезубцев и жемчуга Эндера. */
public final class OrionProjectileTrail {
    private static final int MAX_TRAILS = 96;
    private static final int MAX_POINTS = 110;
    private static final float WIDTH = 0.075F;
    private final OrionConfigManager configManager;
    private final Map<Entity, Trail> trails = new IdentityHashMap<>();
    private ClientLevel level;

    public OrionProjectileTrail(OrionConfigManager configManager) { this.configManager = configManager; }
    public void register() {
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        LevelRenderEvents.COLLECT_SUBMITS.register(this::render);
    }
    private void tick(Minecraft client) {
        OrionConfig config = configManager.getConfig();
        if (!config.isProjectileTrailEnabled() || client.player == null || client.level == null) {
            trails.clear(); level = null; return;
        }
        if (level != client.level) { trails.clear(); level = client.level; }
        long now = System.nanoTime();
        long lifetime = (long) (config.getProjectileTrailDuration() * 1_000_000_000L);
        for (Trail trail : trails.values()) {
            trail.seen = false;
            while (!trail.points.isEmpty() && now - trail.points.peekFirst().time() >= lifetime) trail.points.removeFirst();
        }
        for (Entity entity : client.level.entitiesForRendering()) {
            if (!(entity instanceof AbstractArrow) && !(entity instanceof ThrownEnderpearl)) continue;
            Trail trail = trails.get(entity);
            if (trail == null) {
                if (entity.position().distanceToSqr(entity.getPosition(0F)) <= 0.0001D || trails.size() >= MAX_TRAILS) continue;
                trail = new Trail();
                trail.lastMovementNanos = now;
                trails.put(entity, trail);
            }
            trail.seen = true;
            Vec3 position = entity.position().add(0, entity.getBbHeight() * 0.5D, 0);
            if (trail.lastPosition == null) { trail.lastPosition = position; continue; }
            double distance = position.distanceToSqr(trail.lastPosition);
            if (distance > 64.0D) trail.points.clear();
            else if (distance > 0.0001D) {
                trail.lastMovementNanos = now;
                if (trail.points.isEmpty()) trail.points.addLast(new Point(trail.lastPosition, now));
                trail.points.addLast(new Point(position, now));
                while (trail.points.size() > MAX_POINTS) trail.points.removeFirst();
            }
            trail.lastPosition = position;
        }
        trails.entrySet().removeIf(entry -> entry.getValue().points.isEmpty()
                && (!entry.getValue().seen || now - entry.getValue().lastMovementNanos >= lifetime));
    }
    private void render(LevelRenderContext context) {
        OrionConfig config = configManager.getConfig();
        Minecraft client = Minecraft.getInstance();
        if (!config.isProjectileTrailEnabled() || client.level == null || level != client.level || trails.isEmpty()) return;
        long now = System.nanoTime();
        Vec3 camera = client.gameRenderer.mainCamera().position();
        float partial = client.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        float duration = config.getProjectileTrailDuration();
        int rgb = OrionRainbow.orElse(OrionWorldChanger.parseOpaqueColor(config.getProjectileTrailColor()));
        List<List<OrionRibbon.Point>> paths = new ArrayList<>();
        for (Map.Entry<Entity, Trail> entry : trails.entrySet()) {
            Trail trail = entry.getValue();
            if (trail.points.size() < 2) continue;
            List<Point> points = new ArrayList<>(trail.points);
            Entity entity = entry.getKey();
            Point last = points.get(points.size() - 1);
            if (trail.seen && last.position().distanceToSqr(entity.position().add(0, entity.getBbHeight() * 0.5D, 0)) < 1.0E-6D) {
                points.set(points.size() - 1, new Point(entity.getPosition(partial).add(0, entity.getBbHeight() * 0.5D, 0), last.time()));
            }
            List<OrionRibbon.Point> path = new ArrayList<>(points.size());
            for (int i = 0; i < points.size(); i++) {
                Point point = points.get(i);
                float age = Math.max(0F, 1F - (now - point.time()) / (duration * 1_000_000_000F));
                float tail = Math.min(1F, i / 3F);
                int color = ARGB.color(Math.round(220F * age * tail), ARGB.red(rgb), ARGB.green(rgb), ARGB.blue(rgb));
                path.add(new OrionRibbon.Point(point.position().subtract(camera), color));
            }
            paths.add(OrionRibbon.smooth(path, 3));
        }
        if (paths.isEmpty()) return;
        boolean glow = config.isProjectileTrailGlow();
        PoseStack stack = context.poseStack();
        context.submitNodeCollector().submitCustomGeometry(stack, OrionEspRenderTypes.colorQuadsDepthTested(),
                (pose, vertices) -> { for (List<OrionRibbon.Point> path : paths) OrionRibbon.draw(pose, vertices, path, Vec3.ZERO, WIDTH, glow); });
    }
    private static final class Trail {
        private final ArrayDeque<Point> points = new ArrayDeque<>();
        private Vec3 lastPosition;
        private boolean seen;
        private long lastMovementNanos;
    }
    private record Point(Vec3 position, long time) { }
}
