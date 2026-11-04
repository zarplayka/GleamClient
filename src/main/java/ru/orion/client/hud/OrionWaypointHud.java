package ru.orion.client.hud;

import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import ru.orion.client.OrionClient;
import ru.orion.client.config.OrionConfig;
import ru.orion.client.config.OrionConfigManager;
import ru.orion.client.effect.OrionWaypoints;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
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
    private int nextId;

    public OrionWaypointHud(OrionConfigManager manager) { this.manager = manager; }
    public void register() {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, "waypoint_hud"), this::render);
    }

    private void render(GuiGraphicsExtractor context, DeltaTracker tracker) {
        Minecraft client = Minecraft.getInstance();
        OrionConfig config = manager.getConfig();
        if (client.level != level) { releaseAll(client); level = client.level; }
        if (client.player == null || client.level == null || (!config.isWaypointsEnabled() && !config.isDeathPointEnabled())) {
            releaseAll(client); return;
        }
        if (client.gui.screen() != null) return;
        var camera = client.gameRenderer.gameRenderState().levelRenderState.cameraRenderState;
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
        int scale = Math.max(1, Math.min(6, client.getWindow().getGuiScale()));
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
            Entry entry = entries.computeIfAbsent(point, ignored -> new Entry(nextId++));
            live.add(point);
            entry.update(client, point, candidate.death, scale, detailed, subtitle, coordinates,
                    projection.edge ? Math.round(projection.angle * 16F / (float) Math.PI) : Integer.MIN_VALUE, screenW - EDGE_MARGIN * 2);
            if (entry.texture == null) continue;
            int x = Math.round(projection.x - 14F), y = Math.round(projection.y - entry.height / 2F);
            Bounds bounds = place(x, y, entry.width, entry.height, screenW, screenH, occupied);
            if (bounds == null) continue;
            occupied.add(bounds);
            context.blit(entry.id, bounds.x, bounds.y, bounds.x + entry.width, bounds.y + entry.height, 0F, 1F, 0F, 1F);
            drawn++;
        }
        var iterator = entries.entrySet().iterator();
        while (iterator.hasNext()) {
            var cached = iterator.next();
            if (!live.contains(cached.getKey())) { cached.getValue().release(client); iterator.remove(); }
        }
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

    private final class Entry {
        final Identifier id;
        DynamicTexture texture;
        int width, height, pixelWidth, pixelHeight;
        List<Object> fingerprint;
        long updated;
        Entry(int number) { id = Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, "hud_waypoint_" + number); }
        void update(Minecraft client, OrionConfig.Waypoint point, boolean death, int scale, boolean detailed,
                    String distance, String coords, int angle, int maxWidth) {
            List<Object> next = List.of(point.name, point.color == null ? "" : point.color, death, scale, detailed, distance, coords, angle, maxWidth);
            long now = System.nanoTime();
            if (next.equals(fingerprint)) return;
            if (texture != null && now - updated < UPDATE_NS && fingerprint != null
                    && fingerprint.get(3).equals(scale) && fingerprint.get(4).equals(detailed) && fingerprint.get(8).equals(maxWidth)) return;
            BufferedImage image = drawMarker(point.name, distance, coords, point.color, death, scale, detailed, angle, maxWidth);
            width = image.getWidth() / scale; height = image.getHeight() / scale;
            if (texture == null || image.getWidth() != pixelWidth || image.getHeight() != pixelHeight) {
                release(client);
                NativeImage pixels = new NativeImage(image.getWidth(), image.getHeight(), true);
                OrionHudRenderer.copyPixels(image, pixels);
                texture = new DynamicTexture(() -> "orion_waypoint", pixels);
                client.getTextureManager().register(id, texture);
                pixelWidth = image.getWidth(); pixelHeight = image.getHeight();
            } else {
                var pixels = texture.getPixels();
                if (pixels == null || pixels.isClosed()) { release(client); return; }
                OrionHudRenderer.copyPixels(image, pixels); texture.upload();
            }
            fingerprint = next; updated = now;
        }
        void release(Minecraft client) { if (texture != null) client.getTextureManager().release(id); texture = null; fingerprint = null; }
    }

    private void releaseAll(Minecraft client) { for (Entry entry : entries.values()) entry.release(client); entries.clear(); }

    static BufferedImage drawMarker(String name, String distance, String coords, String color, boolean death, int scale,
                                    boolean detailed, int angle, int maxWidth) {
        Font title = font(Font.BOLD, 9), small = font(Font.PLAIN, 7.5F);
        BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D pg = probe.createGraphics(); hints(pg);
        float available = Math.max(4, maxWidth - 36);
        String shown = ellipsize(pg, title, name, Math.min(130, available));
        String sub = ellipsize(pg, small, distance, available);
        String location = ellipsize(pg, small, coords, available);
        float text = detailed ? Math.max(textWidth(pg, title, shown), Math.max(textWidth(pg, small, sub), textWidth(pg, small, location))) : textWidth(pg, title, sub);
        int width = Math.min(maxWidth, Math.max(48, (int) Math.ceil(32 + text)));
        int height = detailed ? coords.isEmpty() ? 32 : 43 : 24;
        pg.dispose();
        BufferedImage image = new BufferedImage(width * scale, height * scale, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics(); hints(g); g.scale(scale, scale);
        Color accent = death ? new Color(0xFF6262) : OrionHudRenderer.parseColor(color);
        g.setColor(new Color(15, 17, 23, 235)); g.fill(new RoundRectangle2D.Float(.5F, .5F, width - 1, height - 1, 9, 9));
        g.setStroke(new BasicStroke(.65F)); g.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 100));
        g.draw(new RoundRectangle2D.Float(.5F, .5F, width - 1, height - 1, 9, 9));
        g.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 24));
        g.fill(new RoundRectangle2D.Float(5, (height - 18) / 2F, 18, 18, 6, 6));
        Graphics2D symbol = (Graphics2D) g.create();
        symbol.translate(14, height / 2F); symbol.setStroke(new BasicStroke(1.2F, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)); symbol.setColor(accent);
        if (angle != Integer.MIN_VALUE) {
            symbol.rotate(angle * Math.PI / 16);
            Path2D.Float arrow = new Path2D.Float(); arrow.moveTo(5, 0); arrow.lineTo(-4, -4); arrow.lineTo(-2, 0); arrow.lineTo(-4, 4); arrow.closePath(); symbol.fill(arrow);
        } else if (death) {
            symbol.draw(new RoundRectangle2D.Float(-4, -5, 8, 8, 5, 5));
            symbol.fill(new Ellipse2D.Float(-2.5F, -2, 1.8F, 1.8F)); symbol.fill(new Ellipse2D.Float(.7F, -2, 1.8F, 1.8F));
            symbol.draw(new java.awt.geom.Line2D.Float(-2, 3, -2, 5)); symbol.draw(new java.awt.geom.Line2D.Float(2, 3, 2, 5));
        } else {
            Path2D.Float pin = new Path2D.Float(); pin.moveTo(0, 6); pin.curveTo(-9, -2, -4, -6, 0, -6); pin.curveTo(4, -6, 9, -2, 0, 6); pin.closePath(); symbol.draw(pin);
            symbol.draw(new Ellipse2D.Float(-1.8F, -3.8F, 3.6F, 3.6F));
        }
        symbol.dispose();
        if (detailed) {
            drawText(g, title, shown, 28, 12, Color.WHITE);
            drawText(g, small, sub, 28, 24, accent);
            if (!location.isEmpty()) drawText(g, small, location, 28, 36, new Color(164, 172, 190));
        } else drawText(g, title, sub, 28, 15, Color.WHITE);
        g.dispose(); return image;
    }

    private static void drawText(Graphics2D g, Font font, String text, float x, float y, Color color) {
        g.setFont(font); g.setColor(new Color(0, 0, 0, 100)); g.drawString(text, x + .5F, y + .7F);
        g.setColor(color); g.drawString(text, x, y);
    }
    private static Font font(int style, float size) {
        for (String name : new String[]{"Inter", "Segoe UI", Font.SANS_SERIF}) {
            Font font = new Font(name, style, 1).deriveFont(size);
            if (font.canDisplay('Я') && font.canDisplay('ё')) return font;
        }
        return new Font(Font.SANS_SERIF, style, 1).deriveFont(size);
    }
    private static void hints(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
    }
    private static float textWidth(Graphics2D g, Font font, String text) {
        return (float) g.getFontMetrics(font.deriveFont(font.getSize2D() * 16F)).getStringBounds(text, g).getWidth() / 16F;
    }
    private static String ellipsize(Graphics2D g, Font font, String text, float max) {
        if (textWidth(g, font, text) <= max) return text;
        int end = text.length();
        while (end > 0 && textWidth(g, font, text.substring(0, end) + "…") > max) end = text.offsetByCodePoints(end, -1);
        return text.substring(0, end) + "…";
    }
    record Projection(float x, float y, float angle, boolean edge) { }
    record Bounds(int x, int y, int width, int height) {
        boolean intersects(Bounds other) { return x < other.x + other.width + 3 && x + width + 3 > other.x && y < other.y + other.height + 3 && y + height + 3 > other.y; }
    }
    private record Candidate(OrionConfig.Waypoint point, boolean death, double distanceSq) { }
}
