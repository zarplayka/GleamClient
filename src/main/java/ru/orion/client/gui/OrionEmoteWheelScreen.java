package ru.orion.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import ru.orion.client.OrionClient;
import ru.orion.client.effect.OrionEmotes;
import ru.orion.client.hud.OrionHudRenderer;
import ru.orion.client.keybind.OrionKeybinds;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.image.BufferedImage;
import java.util.List;

/** Колесо эмоций с общей зоной выбора для подсветки и нажатия. */
public final class OrionEmoteWheelScreen extends Screen {
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, "emote_wheel");
    private DynamicTexture texture;
    private List<Object> fingerprint;
    private int textureSize;
    private int selected = -1;
    private boolean confirmed;

    public OrionEmoteWheelScreen() { super(Component.literal("Orion Emotes")); }
    @Override public boolean isPauseScreen() { return false; }

    private int size() { return Math.max(1, Math.min(330, Math.min(width, height) - 16)); }
    private int left() { return (width - size()) / 2; }
    private int top() { return (height - size()) / 2; }

    static int sector(double x, double y) {
        double radius = Math.hypot(x, y);
        if (radius > 136 || radius < 57) return -1;
        double angle = Math.toDegrees(Math.atan2(x, -y)) + 30;
        return Math.floorMod((int) Math.floor(angle / 60), 6);
    }

    private int at(double x, double y) {
        double scale = 330D / size();
        return sector((x - left()) * scale - 165, (y - top()) * scale - 165);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);
        if (!OrionEmotes.canDance(minecraft.player)) { onClose(); return; }
        selected = at(mouseX, mouseY);
        int scale = Math.clamp(minecraft.getWindow().getGuiScale(), 1, 6);
        var config = OrionClient.getConfigOrNull();
        boolean light = config != null && config.getClickGuiTheme() == 4;
        List<Object> current = List.of(size(), scale, selected, light, OrionGuiLanguage.english());
        if (!current.equals(fingerprint)) {
            update(scale, light);
            fingerprint = current;
        }
        if (texture != null) context.blit(TEXTURE, left(), top(), left() + size(), top() + size(), 0, 1, 0, 1);
    }

    @Override public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0x66000000);
    }

    private void choose(int sector) {
        if (confirmed) return;
        confirmed = true;
        if (sector >= 0) OrionEmotes.start(sector + 1);
        onClose();
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            double radius = Math.hypot(event.x() - width / 2D, event.y() - height / 2D) * 330D / size();
            if (radius < 52) { OrionEmotes.stop(); onClose(); }
            else choose(at(event.x(), event.y()));
            return true;
        }
        if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) { onClose(); return true; }
        return super.mouseClicked(event, doubleClick);
    }

    @Override public boolean keyPressed(KeyEvent event) {
        if (event.key() >= InputConstants.KEY_1 && event.key() <= InputConstants.KEY_6) {
            choose(event.key() - InputConstants.KEY_1);
            return true;
        }
        if (event.key() == InputConstants.KEY_SPACE) { OrionEmotes.stop(); onClose(); return true; }
        return super.keyPressed(event);
    }

    @Override public boolean keyReleased(KeyEvent event) {
        var key = OrionKeybinds.getEmoteKey();
        if (key != null && key.matches(event)) { choose(selected); return true; }
        return super.keyReleased(event);
    }

    private void update(int scale, boolean light) {
        int pixels = size() * scale;
        BufferedImage image = renderImage(pixels, light, selected);
        if (texture == null || textureSize != pixels) {
            if (texture != null) Minecraft.getInstance().getTextureManager().release(TEXTURE);
            NativeImage nativeImage = new NativeImage(pixels, pixels, true);
            OrionHudRenderer.copyPixels(image, nativeImage);
            texture = new DynamicTexture(() -> "Orion emote wheel", nativeImage);
            Minecraft.getInstance().getTextureManager().register(TEXTURE, texture);
            textureSize = pixels;
        } else {
            OrionHudRenderer.copyPixels(image, texture.getPixels());
            texture.upload();
        }
    }

    static BufferedImage renderImage(int pixels, boolean light, int selected) {
        BufferedImage image = new BufferedImage(pixels, pixels, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g.scale(pixels / 330D, pixels / 330D);
            Color surface = light ? new Color(244, 242, 237, 245) : new Color(26, 28, 32, 245);
            Color text = light ? new Color(28, 29, 34) : new Color(244, 244, 247);
            Color muted = light ? new Color(100, 100, 108) : new Color(162, 166, 176);
            Color accent = new Color(255, 166, 64);
            for (int i = 0; i < 6; i++) {
                Area wedge = new Area(new Arc2D.Float(29, 29, 272, 272, 60 - i * 60 + 1, 58, Arc2D.PIE));
                wedge.subtract(new Area(new Ellipse2D.Float(108, 108, 114, 114)));
                g.setColor(i == selected ? accent : surface);
                g.fill(wedge);
                double angle = Math.toRadians(i * 60);
                float x = 165 + (float) Math.sin(angle) * 96;
                float y = 165 - (float) Math.cos(angle) * 96;
                Color ink = i == selected ? new Color(30, 27, 23) : text;
                drawDancer(g, x, y - 6, i, ink);
                centered(g, Integer.toString(i + 1) + "  " + OrionGuiLanguage.translate(OrionEmotes.NAMES[i]),
                        x, y + 23, 9F, ink);
            }
            g.setColor(surface);
            g.fill(new Ellipse2D.Float(113, 113, 104, 104));
            OrionBrandMark.draw(g, 165, 143, 23, accent);
            centered(g, OrionGuiLanguage.translate("Танцы"), 165, 169, 14, text);
            centered(g, OrionGuiLanguage.translate("Остановить"), 165, 189, 9, muted);
            centered(g, OrionGuiLanguage.translate("Наведи и отпусти клавишу · или нажми 1–6"), 165, 311, 9, text);
            centered(g, OrionGuiLanguage.translate("Движение — завершить · Esc — закрыть"), 165, 325, 8, muted);
        } finally { g.dispose(); }
        return image;
    }

    private static void centered(Graphics2D g, String text, float x, float baseline, float size, Color color) {
        var font = OrionInterfaceFonts.face(1, size);
        var measuring = font.deriveFont(size * 16F);
        float width = (float) g.getFontMetrics(measuring).getStringBounds(text, g).getWidth() / 16F;
        g.setFont(font);
        g.setColor(color);
        g.drawString(text, x - width / 2F, baseline);
    }

    private static void drawDancer(Graphics2D g, float x, float y, int dance, Color color) {
        g.setColor(color);
        g.setStroke(new BasicStroke(2.2F, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.fill(new Ellipse2D.Float(x - 3, y - 19, 6, 6));
        g.draw(new Line2D.Float(x, y - 11, x, y + 2));
        float arm = dance % 2 == 0 ? -15 : 0;
        g.draw(new Line2D.Float(x, y - 8, x - 11, y + arm));
        g.draw(new Line2D.Float(x, y - 8, x + 11, y - 15 + dance % 3 * 5));
        g.draw(new Line2D.Float(x, y + 2, x - 7, y + 12));
        g.draw(new Line2D.Float(x, y + 2, x + 8, y + 9 + dance % 2 * 3));
    }

    @Override public void removed() {
        if (texture != null) Minecraft.getInstance().getTextureManager().release(TEXTURE);
        texture = null;
        fingerprint = null;
        super.removed();
    }
}
