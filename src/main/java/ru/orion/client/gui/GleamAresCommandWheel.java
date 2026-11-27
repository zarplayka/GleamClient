package ru.orion.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import ru.orion.client.GleamMinecraft;
import ru.orion.client.OrionClient;
import ru.orion.client.ares.GleamAresKPHelper;
import ru.orion.client.hud.OrionHudRenderer;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.List;

public final class GleamAresCommandWheel extends Screen {
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, "ares_command_wheel");
    private static final int DESIGN = 326;
    private static final float CENTER = DESIGN / 2F, CENTER_Y = CENTER - 10F;
    private static final double INNER = 47, OUTER = 130, HALF_GAP = 2;
    private static final Font LABEL = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
    private static final Font NICK = LABEL.deriveFont(Font.BOLD, 14F);
    private final AbstractClientPlayer target;
    private final List<String> commands;
    private final List<String> labels;
    private final String name;
    private DynamicTexture texture;
    private int pixels, previousHover = -2;
    private boolean previousLight;
    private Color previousAccent;

    public GleamAresCommandWheel(AbstractClientPlayer target, List<String> commands) {
        super(Component.literal("AresKPHelper"));
        this.target = target;
        this.commands = List.copyOf(commands);
        name = GleamAresKPHelper.name(target);
        labels = commands.stream().map(command -> command.replace("{nick}", name)).toList();
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    //? if >=26.1 {
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) { }
    //?} else {
    /*public void renderBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) { }
    *///?}

    @Override
    //? if >=26.1 {
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
    //?} else {
    /*public void render(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
    *///?}
        if (GleamAresKPHelper.selected(minecraft) != target) {
            onClose();
            return;
        }
        var config = OrionClient.getConfigOrNull();
        boolean light = config.getClickGuiTheme() == 4;
        Color accent = OrionClickGuiScreen.clickGuiAccent(config.getClickGuiAccent());
        int hover = slotAt(mouseX, mouseY);
        int size = size();
        int requiredPixels = size * Math.clamp(minecraft.getWindow().getGuiScale(), 1, 6);
        if (texture == null || pixels != requiredPixels || hover != previousHover || light != previousLight
                || !accent.equals(previousAccent)) {
            upload(renderWheel(requiredPixels, hover, light, accent));
            previousHover = hover;
            previousLight = light;
            previousAccent = accent;
        }
        int x = left(size), y = top(size);
        context.blit(TEXTURE, x, y, x + size, y + size, 0F, 1F, 0F, 1F);
    }

    private BufferedImage renderWheel(int size, int hover, boolean light, Color accent) {
        var image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.scale(size / (double) DESIGN, size / (double) DESIGN);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            Color ink = light ? new Color(26, 31, 44) : new Color(242, 246, 254);
            Color panel = light ? new Color(243, 245, 252, 242) : new Color(22, 26, 39, 244);
            Color border = light ? new Color(44, 52, 75, 42) : new Color(207, 218, 253, 59);
            double step = 360.0 / commands.size();
            var inner = new Ellipse2D.Double(CENTER - INNER, CENTER_Y - INNER, INNER * 2, INNER * 2);
            for (int slot = 0; slot < commands.size(); slot++) {
                double angle = step * slot;
                var shape = new Area(new Arc2D.Double(CENTER - OUTER, CENTER_Y - OUTER, OUTER * 2, OUTER * 2,
                        90 - angle - step / 2 + HALF_GAP, step - HALF_GAP * 2, Arc2D.PIE));
                shape.subtract(new Area(inner));
                g.setColor(hover == slot ? accent : panel);
                g.fill(shape);
                g.setStroke(new BasicStroke(.8F));
                g.setColor(border);
                g.draw(shape);

                double radians = Math.toRadians(angle);
                float x = CENTER + (float) (Math.sin(radians) * 87);
                float y = CENTER_Y - (float) (Math.cos(radians) * 87);
                Color text = hover == slot ? (dark(accent) ? Color.WHITE : new Color(17, 20, 32)) : ink;
                String label = commands.get(slot).split(" ", 2)[0];
                drawCentered(g, NICK, label, x, y + 4, 69, text);
                drawCentered(g, LABEL, Integer.toString(slot + 1), x, y - 13, 24, text);
            }
            g.setColor(panel);
            g.fill(new Ellipse2D.Double(CENTER - 42, CENTER_Y - 42, 84, 84));
            g.setColor(border);
            g.draw(new Ellipse2D.Double(CENTER - 42, CENTER_Y - 42, 84, 84));
            float nameWidth = g.getFontMetrics(NICK.deriveFont(NICK.getSize2D() * 16F)).stringWidth(name) / 16F;
            float nameSize = Math.min(NICK.getSize2D(), NICK.getSize2D() * 73F / Math.max(1F, nameWidth));
            drawCentered(g, NICK.deriveFont(nameSize), name, CENTER, CENTER_Y, 74F, ink);

            if (hover >= 0) {
                var pill = new RoundRectangle2D.Float(17F, DESIGN - 33F, DESIGN - 34F, 26F, 26F, 26F);
                g.setColor(panel);
                g.fill(pill);
                g.setColor(border);
                g.draw(pill);
                drawCentered(g, LABEL, labels.get(hover), CENTER, DESIGN - 20F, DESIGN - 54F, ink);
            }
        } finally { g.dispose(); }
        return image;
    }

    private static boolean dark(Color color) {
        return .2126 * color.getRed() + .7152 * color.getGreen() + .0722 * color.getBlue() < 135;
    }

    private static void drawCentered(Graphics2D g, Font font, String label, float x, float y, float maxWidth, Color color) {
        var measuring = font.deriveFont(font.getSize2D() * 16F);
        var metrics = g.getFontMetrics(measuring);
        String shown = label;
        if (metrics.stringWidth(label) > maxWidth * 16F) {
            int end = label.length();
            do {
                end = label.offsetByCodePoints(end, -1);
                shown = label.substring(0, end) + "…";
            } while (end > 0 && metrics.stringWidth(shown) > maxWidth * 16F);
        }
        g.setColor(color);
        g.setFont(font);
        g.drawString(shown, x - metrics.stringWidth(shown) / 32F,
                y + (metrics.getAscent() - metrics.getDescent()) / 32F);
    }

    private void upload(BufferedImage image) {
        int size = image.getWidth();
        if (texture != null && pixels == size && texture.getPixels() != null) {
            OrionHudRenderer.copyPixels(image, texture.getPixels());
            texture.upload();
            return;
        }
        release();
        NativeImage nativeImage = new NativeImage(size, size, false);
        OrionHudRenderer.copyPixels(image, nativeImage);
        texture = new DynamicTexture(() -> "Ares command wheel", nativeImage);
        minecraft.getTextureManager().register(TEXTURE, texture);
        pixels = size;
    }

    private void release() {
        if (texture == null) return;
        minecraft.getTextureManager().release(TEXTURE);
        texture = null;
    }

    private int size() { return Math.max(1, Math.min(DESIGN, Math.min(width, height) - 8)); }
    private int left(int size) { return (width - size) / 2; }
    private int top(int size) { return (height - size) / 2; }

    private int slotAt(double x, double y) {
        int side = size();
        double dx = (x - left(side)) * DESIGN / side - CENTER;
        double dy = (y - top(side)) * DESIGN / side - CENTER_Y;
        double radius = Math.hypot(dx, dy);
        if (radius < INNER || radius > OUTER) return -1;
        double step = 360.0 / commands.size();
        double angle = (Math.toDegrees(Math.atan2(dx, -dy)) + 360 + step / 2) % 360;
        double within = angle % step;
        return within < HALF_GAP || within > step - HALF_GAP ? -1 : (int) (angle / step);
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int slot = slotAt(event.x(), event.y());
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && slot >= 0) {
            choose(slot);
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override public boolean keyPressed(KeyEvent event) {
        int slot = event.key() - InputConstants.KEY_1;
        if (slot >= 0 && slot < commands.size() && !event.hasControlDown() && !event.hasAltDown() && !event.hasShiftDown()) {
            choose(slot);
            return true;
        }
        return super.keyPressed(event);
    }

    private void choose(int slot) {
        if (GleamMinecraft.screen(minecraft) != this) return;
        GleamAresKPHelper.execute(minecraft, target, commands.get(slot));
        onClose();
    }

    @Override public void onClose() { GleamMinecraft.setScreen(minecraft, null); }

    @Override public void removed() { release(); }
}
