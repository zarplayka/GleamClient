package ru.orion.client.hud;

import ru.orion.client.GleamMinecraft;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import ru.orion.client.OrionClient;
import ru.orion.client.config.OrionConfig;
import ru.orion.client.config.OrionConfigManager;
import ru.orion.client.effect.OrionWaypoints;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Экранные метки с указателями вне поля зрения и подробностями при наведении. */
public final class OrionWaypointHud {
    private static final int EDGE_MARGIN = 12;
    private static final long UPDATE_NS = 200_000_000L;
    private final OrionConfigManager manager;
    private final Map<OrionConfig.Waypoint, Entry> entries = new IdentityHashMap<>();
    private Object level;
    private final OrionHudView view = new OrionHudView("hud_waypoints");

    public OrionWaypointHud(OrionConfigManager manager) { this.manager = manager; }
    public void register() {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, "waypoint_hud"), this::render);
    }

    private void render(GuiGraphicsExtractor context, DeltaTracker tracker) {
        Minecraft client = Minecraft.getInstance();
        OrionConfig config = manager.getConfig();
        if (client.level != level) { releaseAll(); level = client.level; }
        if (client.player == null || client.level == null || (!config.isWaypointsEnabled() && !config.isDeathPointEnabled())) {
            releaseAll(); return;
        }
        if (GleamMinecraft.screen(client) != null) return;
        var camera = GleamMinecraft.renderState(client).levelRenderState.cameraRenderState;
        if (camera == null || !camera.initialized || camera.pos == null || camera.projectionMatrix == null || camera.viewRotationMatrix == null) return;
        int screenW = context.guiWidth(), screenH = context.guiHeight();
        if (screenW < 72 || screenH < 48) return;
        Matrix4f matrix = new Matrix4f(camera.projectionMatrix).mul(camera.viewRotationMatrix);
        String world = OrionWaypoints.worldKey(client);
        List<Candidate> candidates = new ArrayList<>();
        if (config.isWaypointsEnabled()) for (var point : config.getWaypoints()) {
            if (world.equals(point.world)) candidates.add(new Candidate(point, false, client.player.position().distanceToSqr(point.x + .5, point.y, point.z + .5)));
        }
        var death = config.getDeathPoint();
        if (config.isDeathPointEnabled() && death != null && world.equals(death.world))
            candidates.add(new Candidate(death, true, client.player.position().distanceToSqr(death.x + .5, death.y, death.z + .5)));
        candidates.sort(Comparator.comparing((Candidate c) -> !c.death).thenComparingDouble(Candidate::distanceSq));
        Set<OrionConfig.Waypoint> live = Collections.newSetFromMap(new IdentityHashMap<>());
        List<Bounds> occupied = new ArrayList<>();
        var scene = new OrionHudView.Scene(screenW, screenH);
        int drawn = 0, considered = 0;
        for (Candidate candidate : candidates) {
            if (drawn >= config.getWaypointVisibleLimit()) break;
            var point = candidate.point;
            Vector4f clip = matrix.transform(new Vector4f((float) (point.x + .5 - camera.pos.x),
                    (float) (point.y + 1 - camera.pos.y), (float) (point.z + .5 - camera.pos.z), 1));
            Projection projection = project(clip, screenW, screenH);
            if (projection == null || projection.edge && !config.isWaypointEdgeIndicators()) continue;
            if (considered++ >= config.getWaypointVisibleLimit() * 2) break;
            double distance = Math.sqrt(candidate.distanceSq);
            boolean detailed = config.isWaypointSmartLabels() && directlyAimed(projection, screenW, screenH);
            String subtitle = distance < 3 ? "Вы на месте" : formatDistance(distance);
            int altitude = (int) Math.round(point.y - client.player.getY());
            if (detailed && Math.abs(altitude) > 2) subtitle += " · " + (altitude > 0 ? "+" : "") + altitude + " м";
            String coordinates = detailed && config.isWaypointShowCoordinates() ? point.x + " · " + point.y + " · " + point.z : "";
            Entry entry = entries.computeIfAbsent(point, ignored -> new Entry());
            live.add(point);
            int angle = projection.edge ? Math.round(projection.angle * 16F / (float) Math.PI) : Integer.MIN_VALUE;
            entry.update(point, candidate.death, detailed, subtitle, coordinates, angle, screenW - EDGE_MARGIN * 2);
            int x = Math.round(projection.x - 14F), y = Math.round(projection.y - entry.height / 2F);
            Bounds bounds = place(x, y, entry.width, entry.height, screenW, screenH, occupied);
            if (bounds == null) continue;
            occupied.add(bounds);
            entry.draw(scene, bounds.x, bounds.y);
            drawn++;
        }
        entries.keySet().removeIf(point -> !live.contains(point));
        if (drawn != 0) view.draw(context, scene, 0, 0, screenW, screenH);
    }

    static boolean directlyAimed(Projection projection, int width, int height) {
        return !projection.edge && Math.hypot(projection.x - width / 2F, projection.y - height / 2F) <= 7F;
    }

    static Projection project(Vector4f clip, int width, int height) {
        if (!Float.isFinite(clip.x) || !Float.isFinite(clip.y) || !Float.isFinite(clip.w)) return null;
        float divisor = Math.max(.001F, Math.abs(clip.w));
        float dx = clip.x / divisor * width / 2F, dy = -clip.y / divisor * height / 2F;
        if (!Float.isFinite(dx) || !Float.isFinite(dy)) return null;
        float limitX = Math.max(1, width / 2F - EDGE_MARGIN - 24), limitY = Math.max(1, height / 2F - EDGE_MARGIN - 16);
        boolean edge = clip.w <= .001F || Math.abs(dx) > limitX || Math.abs(dy) > limitY;
        if (edge) {
            if (Math.abs(dx) + Math.abs(dy) < .01F) dy = 1;
            float factor = Math.min(limitX / Math.max(.001F, Math.abs(dx)), limitY / Math.max(.001F, Math.abs(dy)));
            dx *= factor; dy *= factor;
        }
        return new Projection(width / 2F + dx, height / 2F + dy, (float) Math.atan2(dy, dx), edge);
    }

    static Bounds place(int x, int y, int width, int height, int screenW, int screenH, List<Bounds> occupied) {
        if (width > screenW - 4 || height > screenH - 4) return null;
        x = Math.max(2, Math.min(screenW - width - 2, x));
        y = Math.max(2, Math.min(screenH - height - 2, y));
        for (int attempt = 0; attempt < 15; attempt++) {
            int offset = attempt == 0 ? 0 : ((attempt + 1) / 2) * (height + 4) * ((attempt & 1) == 1 ? 1 : -1);
            int cy = y + offset;
            if (cy < 2 || cy + height > screenH - 2) continue;
            Bounds candidate = new Bounds(x, cy, width, height);
            if (occupied.stream().noneMatch(candidate::intersects)) return candidate;
        }
        return null;
    }

    static String formatDistance(double distance) {
        return distance >= 1000 ? String.format(Locale.ROOT, "%.1f км", distance / 1000) : Math.round(Math.max(0, distance)) + " м";
    }

    private static final class Entry {
        private int width, height;
        private Marker marker;
        private long updated;

        void update(OrionConfig.Waypoint point, boolean death, boolean detailed,
                    String distance, String coords, int angle, int maxWidth) {
            Marker next = new Marker(point.name, point.color, death, detailed, distance, coords, angle, maxWidth);
            long now = System.nanoTime();
            if (next.equals(marker) || (marker != null && now - updated < UPDATE_NS
                    && marker.detailed == detailed && marker.maxWidth == maxWidth && marker.angle == angle)) return;
            var font = Minecraft.getInstance().font;
            int text = font.width(distance);
            if (detailed) text = Math.max(text, Math.max(Math.min(130, font.width(point.name)), font.width(coords)));
            width = Math.min(maxWidth, Math.max(48, 32 + text));
            height = detailed ? coords.isEmpty() ? 32 : 43 : 24;
            marker = next;
            updated = now;
        }

        void draw(OrionHudView.Scene scene, int x, int y) {
            int rgb = marker.death ? 0xffff6262 : ru.orion.client.effect.OrionWorldChanger.parseOpaqueColor(marker.color);
            String accent = OrionHudView.color(rgb);
            scene.surface(x, y, width, height, 6);
            scene.box(x + 5, y + (height - 18) / 2F, 18, 18, 3, OrionHudView.color(rgb & 0xffffff | 0x18000000), null);
            boolean edge = marker.angle != Integer.MIN_VALUE;
            String icon = edge ? OrionHudView.arrow(marker.angle * 11.25F + 90)
                    : marker.death ? "M4 12V8a6 6 0 0 1 12 0v4l-3 2v3H7v-3Z M7 9h1 M12 9h1"
                    : "M10 18C-2 7 4 2 10 2S22 7 10 18Z M10 5a3 3 0 1 1 0 6a3 3 0 1 1 0-6";
            scene.icon(icon, x + 7, y + (height - 14) / 2F, 14, accent, edge);
            if (marker.detailed) {
                scene.label(marker.name, x + 28, y + 2, width - 32, 12, 9, "var(--text)", "left", true);
                scene.label(marker.distance, x + 28, y + 14, width - 32, 12, 7.5F, accent, "left", false);
                if (!marker.coords.isEmpty())
                    scene.label(marker.coords, x + 28, y + 26, width - 32, 12, 7.5F, "var(--muted)", "left", false);
            } else {
                scene.label(marker.distance, x + 28, y, width - 32, height, 9, "var(--text)", "left", true);
            }
        }
    }

    private record Marker(String name, String color, boolean death, boolean detailed, String distance,
                          String coords, int angle, int maxWidth) {}

    private void releaseAll() {
        entries.clear();
        view.dispose();
    }

    record Projection(float x, float y, float angle, boolean edge) { }
    record Bounds(int x, int y, int width, int height) {
        boolean intersects(Bounds other) { return x < other.x + other.width + 3 && x + width + 3 > other.x && y < other.y + other.height + 3 && y + height + 3 > other.y; }
    }
    private record Candidate(OrionConfig.Waypoint point, boolean death, double distanceSq) { }
}
