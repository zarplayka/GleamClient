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

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/** Чёткие экранные метки, привязанные к координатам мира. */
public final class OrionWaypointHud {
    private static final int MAX_TEXTURE_SCALE = 6;
    private static final int HEIGHT = 29;
    private static final int PADDING = 5;
    private static final int ICON_SIZE = 20;
    private static final int MAX_NAME_WIDTH = 120;
    private static final long DISTANCE_UPDATE_NS = 250_000_000L;
    private final OrionConfigManager manager;
    private final Map<OrionConfig.Waypoint, Entry> entries = new IdentityHashMap<>();
    private final BufferedImage icon;
    private final Map<Integer, BufferedImage> coloredIcons = new HashMap<>();
    private int nextId;

    public OrionWaypointHud(OrionConfigManager manager) {
        this.manager = manager;
        BufferedImage loaded = null;
        try (var stream = OrionWaypointHud.class.getResourceAsStream("/assets/orion-client/textures/gui/waypoint_icon.png")) {
            if (stream != null) loaded = ImageIO.read(stream);
        } catch (Exception ignored) { }
        icon = loaded;
    }

    public void register() {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, "waypoint_hud"), this::render);
    }

    private void render(GuiGraphicsExtractor context, DeltaTracker tracker) {
        Minecraft client = Minecraft.getInstance();
        OrionConfig config = manager.getConfig();
        if (client.player == null || client.level == null || (!config.isWaypointsEnabled() && !config.isDeathPointEnabled())) {
            releaseAll(client);
            return;
        }
        var camera = client.gameRenderer.gameRenderState().levelRenderState.cameraRenderState;
        if (camera == null || !camera.initialized || camera.pos == null || camera.projectionMatrix == null || camera.viewRotationMatrix == null) return;
        Matrix4f viewProjection = new Matrix4f(camera.projectionMatrix).mul(camera.viewRotationMatrix);
        String world = OrionWaypoints.worldKey(client);
        Set<OrionConfig.Waypoint> live = Collections.newSetFromMap(new IdentityHashMap<>());
        int scale = Math.max(1, Math.min(MAX_TEXTURE_SCALE, client.getWindow().getGuiScale()));
        if (config.isWaypointsEnabled()) for (OrionConfig.Waypoint point : config.getWaypoints()) {
            if (world.equals(point.world)) {
                live.add(point);
                draw(context, client, point, viewProjection, camera.pos, scale, false);
            }
        }
        OrionConfig.Waypoint death = config.getDeathPoint();
        if (config.isDeathPointEnabled() && death != null && world.equals(death.world)) {
            live.add(death);
            draw(context, client, death, viewProjection, camera.pos, scale, true);
        }
        Iterator<Map.Entry<OrionConfig.Waypoint, Entry>> iterator = entries.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<OrionConfig.Waypoint, Entry> cached = iterator.next();
            if (!live.contains(cached.getKey())) {
                cached.getValue().release(client);
                iterator.remove();
            }
        }
    }

    private void draw(GuiGraphicsExtractor context, Minecraft client, OrionConfig.Waypoint point, Matrix4f matrix,
                      net.minecraft.world.phys.Vec3 cameraPos, int scale, boolean death) {
        double dx = point.x + 0.5D - cameraPos.x;
        double dy = point.y + 1.0D - cameraPos.y;
        double dz = point.z + 0.5D - cameraPos.z;
        Vector4f clip = matrix.transform(new Vector4f((float) dx, (float) dy, (float) dz, 1F));
        if (!Float.isFinite(clip.w) || clip.w <= 0.01F) return;
        float sx = (clip.x / clip.w * 0.5F + 0.5F) * context.guiWidth();
        float sy = (0.5F - clip.y / clip.w * 0.5F) * context.guiHeight();
        if (!Float.isFinite(sx) || !Float.isFinite(sy)) return;
        double distance = client.player.position().distanceToSqr(point.x + .5D, point.y + 1D, point.z + .5D);
        String distanceText = Math.round(Math.sqrt(distance)) + " м";
        Entry entry = entries.computeIfAbsent(point, ignored -> new Entry(nextId++));
        entry.update(client, point, scale, distanceText, death);
        if (entry.texture == null) return;
        int x = Math.round(Math.max(2, Math.min(context.guiWidth() - entry.width - 2, sx - entry.width / 2F)));
        int y = Math.round(Math.max(2, Math.min(context.guiHeight() - HEIGHT - 2, sy - HEIGHT / 2F)));
        context.blit(entry.id, x, y, x + entry.width, y + HEIGHT, 0F, 1F, 0F, 1F);
    }

    private final class Entry {
        final Identifier id;
        DynamicTexture texture;
        int pixelWidth, pixelHeight, width, scale;
        String name = "", color = "", distance = "";
        boolean death;
        long updated;
        Entry(int number) { id = Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, "hud_waypoint_" + number); }

        void update(Minecraft client, OrionConfig.Waypoint point, int newScale, String newDistance, boolean isDeath) {
            long now = System.nanoTime();
            boolean dataChanged = texture == null || scale != newScale || !name.equals(point.name)
                    || !color.equals(point.color == null ? "" : point.color) || death != isDeath;
            if (!dataChanged && (distance.equals(newDistance) || now - updated < DISTANCE_UPDATE_NS)) return;
            name = point.name; color = point.color == null ? "" : point.color; death = isDeath; scale = newScale;
            distance = newDistance; updated = now;
            BufferedImage image = drawMarker(name, distance, point.color, isDeath, scale, iconFor(parse(point.color, isDeath)));
            int pw = image.getWidth(), ph = image.getHeight();
            width = pw / scale;
            if (texture == null || pixelWidth != pw || pixelHeight != ph) {
                if (texture != null) client.getTextureManager().release(id);
                NativeImage pixels = new NativeImage(pw, ph, true);
                copyPixels(image, pixels);
                texture = new DynamicTexture(() -> "orion_waypoint", pixels);
                client.getTextureManager().register(id, texture);
                pixelWidth = pw; pixelHeight = ph;
            } else {
                NativeImage pixels = texture.getPixels();
                if (pixels == null || pixels.isClosed()) { release(client); return; }
                copyPixels(image, pixels);
                texture.upload();
            }
        }
        void release(Minecraft client) { if (texture != null) client.getTextureManager().release(id); texture = null; }
    }

    private void releaseAll(Minecraft client) { for (Entry entry : entries.values()) entry.release(client); entries.clear(); }
    private BufferedImage iconFor(Color color) {
        if (icon == null) return null;
        int rgb = color.getRGB() & 0xFFFFFF;
        if (rgb == 0xFF9B38) return icon;
        BufferedImage cached = coloredIcons.get(rgb);
        if (cached != null) return cached;
        if (coloredIcons.size() >= 64) coloredIcons.clear();
        BufferedImage result = new BufferedImage(icon.getWidth(), icon.getHeight(), BufferedImage.TYPE_INT_ARGB);
        float hue = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null)[0];
        float[] hsb = new float[3];
        for (int y = 0; y < icon.getHeight(); y++) for (int x = 0; x < icon.getWidth(); x++) {
            int pixel = icon.getRGB(x, y), alpha = pixel >>> 24;
            if (alpha == 0) continue;
            Color.RGBtoHSB(pixel >> 16 & 255, pixel >> 8 & 255, pixel & 255, hsb);
            int recolored = hsb[1] < 0.12F ? pixel : Color.HSBtoRGB(hue, hsb[1], hsb[2]);
            result.setRGB(x, y, alpha << 24 | recolored & 0xFFFFFF);
        }
        coloredIcons.put(rgb, result);
        return result;
    }
    static BufferedImage drawMarker(String name, String distance, String color, boolean death, int scale, BufferedImage icon) {
        Font title = font(Font.BOLD, 9F), subtitle = font(Font.PLAIN, 7.5F);
        BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D pg = probe.createGraphics();
        hints(pg);
        String shown = ellipsize(pg, title, name, MAX_NAME_WIDTH);
        int width = Math.max(55, (int) Math.ceil(PADDING + ICON_SIZE + 5 + Math.max(textWidth(pg, title, shown), textWidth(pg, subtitle, distance)) + PADDING + 2));
        pg.dispose();
        BufferedImage image = new BufferedImage(width * scale, HEIGHT * scale, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        hints(g); g.scale(scale, scale);
        g.setColor(new Color(10, 11, 17, 225));
        g.fill(new RoundRectangle2D.Float(0, 0, width, HEIGHT, 10, 10));
        Color accent = parse(color, death);
        g.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 210));
        g.fill(new RoundRectangle2D.Float(0, 0, 3, HEIGHT, 3, 3));
        if (icon != null) g.drawImage(icon, PADDING, (HEIGHT - ICON_SIZE) / 2, ICON_SIZE, ICON_SIZE, null);
        else { g.setColor(accent); g.fill(new Ellipse2D.Float(PADDING + 4, 8, 12, 12)); }
        g.setFont(title); g.setColor(Color.WHITE); g.drawString(shown, PADDING + ICON_SIZE + 5F, 12F);
        g.setFont(subtitle); g.setColor(new Color(200, 205, 216)); g.drawString(distance, PADDING + ICON_SIZE + 5F, 22F);
        g.dispose();
        return image;
    }
    private static void copyPixels(BufferedImage source, NativeImage target) {
        int w = source.getWidth(), h = source.getHeight();
        int[] argb = source.getRGB(0, 0, w, h, null, 0, w);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) target.setPixel(x, y, argb[y * w + x]);
    }
    private static void hints(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
    }
    private static Font font(int style, float size) {
        for (String family : new String[]{"Inter", "Segoe UI"}) {
            Font font = new Font(family, style, 12);
            if (font.getFamily().equalsIgnoreCase(family) && font.canDisplay('Я') && font.canDisplay('ё')) return font.deriveFont(style, size);
        }
        return new Font(Font.SANS_SERIF, style, 12).deriveFont(style, size);
    }
    private static float textWidth(Graphics2D g, Font font, String text) {
        Font measuring = font.deriveFont(font.getSize2D() * 16F);
        return (float) g.getFontMetrics(measuring).getStringBounds(text, g).getWidth() / 16F;
    }
    private static String ellipsize(Graphics2D g, Font font, String text, float max) {
        if (textWidth(g, font, text) <= max) return text;
        int end = text.length();
        while (end > 0 && textWidth(g, font, text.substring(0, end) + "…") > max) end = text.offsetByCodePoints(end, -1);
        return text.substring(0, end) + "…";
    }
    private static Color parse(String value, boolean death) {
        if (death) return new Color(0xFF6262);
        return OrionHudRenderer.parseColor(value);
    }
}
