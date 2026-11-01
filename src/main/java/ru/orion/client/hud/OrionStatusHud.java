package ru.orion.client.hud;

import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import ru.orion.client.OrionClient;
import ru.orion.client.config.OrionConfigManager;
import ru.orion.client.effect.OrionWorldChanger;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/** Компактные HUD-панели на одной текстуре, обновляемой при изменении данных. */
public final class OrionStatusHud {
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, "hud_status_panels");
    private final OrionConfigManager configManager;
    private DynamicTexture texture;
    private final OrionHudDrag drag = new OrionHudDrag("keystrokes");
    private List<Object> fingerprint;
    private int pixelWidth, pixelHeight, guiWidth, guiHeight;
    public OrionStatusHud(OrionConfigManager configManager) { this.configManager = configManager; }
    public void register() { HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, "status_panels"), this::render); }

    private void render(GuiGraphicsExtractor context, DeltaTracker tickCounter) {
        var client = Minecraft.getInstance();
        var config = configManager.getConfig();
        if (client.player == null || !config.isKeystrokesEnabled()) { release(); return; }
        KeyMapping[] keys = {client.options.keyUp, client.options.keyLeft, client.options.keyDown, client.options.keyRight,
                client.options.keyJump, client.options.keyAttack, client.options.keyUse};
        List<String> labels = new ArrayList<>(keys.length);
        List<Boolean> pressed = new ArrayList<>(keys.length);
        for (KeyMapping key : keys) pressed.add(key.isDown());
        labels.addAll(List.of("W", "A", "S", "D", "SPACE"));
        int leftCps = ru.orion.client.effect.OrionClickCounter.count(false);
        int rightCps = ru.orion.client.effect.OrionClickCounter.count(true);
        int scale = Math.max(1, Math.min(6, client.getWindow().getGuiScale()));
        float elementScale=config.getHudScale()*config.getHudElementScale("keystrokes");
        List<Object> next = List.of(labels, pressed, leftCps, rightCps, config.getAccentColor(), scale,
                elementScale, context.guiWidth(), context.guiHeight());
        if (!next.equals(fingerprint)) {
            update(labels, pressed, leftCps, rightCps, OrionWorldChanger.parseOpaqueColor(config.getAccentColor()),
                    scale, elementScale, context.guiWidth(), context.guiHeight());
            fingerprint = next;
        }
        if (texture == null) return;
        int x = config.getKeystrokesX() < 0 ? 8 : config.getKeystrokesX();
        int y = config.getKeystrokesY() < 0 ? context.guiHeight() - guiHeight - 45 : config.getKeystrokesY();
        x = Math.max(0,Math.min(x,context.guiWidth()-guiWidth)); y = Math.max(0,Math.min(y,context.guiHeight()-guiHeight));
        var position=drag.update(context,x,y,guiWidth,guiHeight,configManager,(nx,ny)->{config.setKeystrokesX(nx);config.setKeystrokesY(ny);});
        if(!OrionGlassCompositor.INSTANCE.submit(TEXTURE,context,texture,position.x(),position.y(),guiWidth,guiHeight))
            context.blit(TEXTURE, position.x(), position.y(), position.x() + guiWidth, position.y() + guiHeight, 0F, 1F, 0F, 1F);
    }

    private void update(List<String> labels, List<Boolean> pressed, int leftCps, int rightCps,
                        int rgb, int scale, float hudScale, int screenWidth, int screenHeight) {
        Font font = pickFont(7.5F);
        float designWidth = 66F, designHeight = 72F;
        float fit = Math.min(hudScale, Math.min(Math.max(1, screenWidth - 8) / designWidth, Math.max(1, screenHeight - 8) / designHeight));
        guiWidth = Math.max(1, Math.round(designWidth * fit)); guiHeight = Math.max(1, Math.round(designHeight * fit));
        BufferedImage image = new BufferedImage(guiWidth * scale, guiHeight * scale, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g.scale(image.getWidth() / (double) designWidth, image.getHeight() / (double) designHeight);
            Color accent = new Color(rgb, true);
            drawKey(g, font, labels.get(0), pressed.get(0), 23, 0, 20, 20, accent);
            for (int i = 0; i < 3; i++) drawKey(g, font, labels.get(i + 1), pressed.get(i + 1), i * 23, 23, 20, 20, accent);
            drawKey(g, font, labels.get(4), pressed.get(4), 0, 46, 66, 10, accent);
            Font cpsFont=font.deriveFont(6.2F);
            drawKey(g, cpsFont, "L " + leftCps + " CPS", pressed.get(5), 0, 59, 31.5F, 13, accent);
            drawKey(g, cpsFont, "R " + rightCps + " CPS", pressed.get(6), 34.5F, 59, 31.5F, 13, accent);
        } finally { g.dispose(); }
        upload(image);
        OrionGlassCompositor.INSTANCE.updateMask(TEXTURE,new OrionGlassCompositor.MaskLayout(image.getWidth(),image.getHeight(),designWidth,designHeight,
                List.of(new OrionGlassCompositor.Panel(23,0,20,20,7),new OrionGlassCompositor.Panel(0,23,20,20,7),
                        new OrionGlassCompositor.Panel(23,23,20,20,7),new OrionGlassCompositor.Panel(46,23,20,20,7),
                        new OrionGlassCompositor.Panel(0,46,66,10,7),new OrionGlassCompositor.Panel(0,59,31.5F,13,7),
                        new OrionGlassCompositor.Panel(34.5F,59,31.5F,13,7))));
    }
    private static void drawKey(Graphics2D g, Font font, String label, boolean down, float x, float y, float w, float h, Color accent) {
        panel(g, x, y, w, h, down, accent); text(g, font, label, x + w / 2, y + h / 2, w - 5);
    }
    private static void panel(Graphics2D g, float x, float y, float w, float h, boolean active, Color accent) {
        OrionGlassStyle.drawPanel(g,x,y,w,h,7);
        if(active){g.setColor(new Color(accent.getRed(),accent.getGreen(),accent.getBlue(),150));g.fill(new RoundRectangle2D.Float(x,y,w,h,7,7));}
        g.setStroke(new BasicStroke(1F)); g.setColor(new Color(255, 255, 255, 50));
        g.draw(new RoundRectangle2D.Float(x + 0.5F, y + 0.5F, w - 1, h - 1, 7, 7));
    }
    private static void text(Graphics2D g, Font font, String value, float centerX, float centerY, float maxWidth) {
        while (!value.isEmpty() && width(g, font, value) > maxWidth) value = value.substring(0, value.offsetByCodePoints(value.length(), -1));
        g.setFont(font); var metrics = g.getFontMetrics();
        float x = centerX - width(g, font, value) / 2F, baseline = centerY + (metrics.getAscent() - metrics.getDescent()) / 2F;
        g.setColor(new Color(0, 0, 0, 90)); g.drawString(value, x + 0.7F, baseline + 0.9F);
        g.setColor(Color.WHITE); g.drawString(value, x, baseline);
    }
    private static float width(Graphics2D g, Font font, String text) {
        Font measuring = font.deriveFont(font.getSize2D() * 16F);
        return (float) g.getFontMetrics(measuring).getStringBounds(text, g).getWidth() / 16F;
    }
    private static Font pickFont(float size) {
        for (String family : new String[]{"Inter", "Segoe UI"}) {
            Font font = new Font(family, Font.BOLD, 8);
            if (font.getFamily().equalsIgnoreCase(family) && font.canDisplay('Я') && font.canDisplay('ё')) return font.deriveFont(size);
        }
        return new Font(Font.SANS_SERIF, Font.BOLD, 8).deriveFont(size);
    }
    private void upload(BufferedImage image) {
        Minecraft client = Minecraft.getInstance(); int w = image.getWidth(), h = image.getHeight();
        if (texture == null || pixelWidth != w || pixelHeight != h) {
            client.getTextureManager().release(TEXTURE);
            NativeImage pixels = new NativeImage(w, h, true); OrionHudRenderer.copyPixels(image, pixels);
            texture = new DynamicTexture(() -> "orion_status_panels", pixels); client.getTextureManager().register(TEXTURE, texture);
            pixelWidth = w; pixelHeight = h;
        } else {
            NativeImage pixels = texture.getPixels();
            if (pixels == null || pixels.isClosed()) { fingerprint = null; return; }
            OrionHudRenderer.copyPixels(image, pixels); texture.upload();
        }
    }
    private void release() {
        drag.cancel(configManager);
        if (texture != null) Minecraft.getInstance().getTextureManager().release(TEXTURE);
        OrionGlassCompositor.INSTANCE.releaseMask(TEXTURE);
        texture = null; fingerprint = null;
    }
}
