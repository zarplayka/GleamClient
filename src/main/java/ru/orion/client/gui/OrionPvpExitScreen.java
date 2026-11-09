package ru.orion.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import ru.orion.client.OrionClient;
import ru.orion.client.effect.OrionPvpSafe;
import ru.orion.client.hud.OrionGlassStyle;
import ru.orion.client.hud.OrionHudRenderer;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.List;

/** Подтверждение выхода в боевом режиме. */
public final class OrionPvpExitScreen extends Screen {
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, "pvp_exit_dialog");
    private static final int DESIGN_W = 264, DESIGN_H = 112;
    private static final int BUTTON_Y = 77, BUTTON_H = 22;
    private final Screen previous;
    private DynamicTexture texture;
    private int guiW, guiH, pixelW, pixelH;
    private List<Object> fingerprint;
    public OrionPvpExitScreen(Screen previous) { super(Component.literal(tr("Выход во время боя"))); this.previous = previous; }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0x99000000);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);
        int scale = Math.max(1, Math.min(6, Minecraft.getInstance().getWindow().getGuiScale()));
        float fit = Math.max(0.01F, Math.min(1F, Math.min((width - 12F) / DESIGN_W, (height - 12F) / DESIGN_H)));
        guiW = Math.max(1, Math.round(DESIGN_W * fit)); guiH = Math.max(1, Math.round(DESIGN_H * fit));
        int remaining = OrionPvpSafe.remainingSeconds();
        var config = OrionClient.getInstance().getConfigManager().getConfig();
        boolean light = config.getClickGuiTheme() == 4;
        String accent = config.getClickGuiAccent();
        int hovered = actionAt(mouseX, mouseY);
        List<Object> next = List.of(scale, guiW, guiH, remaining, light, accent, hovered, OrionGuiLanguage.language());
        if (!next.equals(fingerprint)) { update(scale, remaining, light, accent, hovered); fingerprint = next; }
        if (texture != null) context.blit(TEXTURE, x(), y(), x() + guiW, y() + guiH, 0, 1, 0, 1);
    }
    private int x() { return (width - guiW) / 2; }
    private int y() { return (height - guiH) / 2; }
    private void update(int scale, int remaining, boolean light, String accent, int hovered) {
        int w = guiW * scale, h = guiH * scale;
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.scale(w / (double) DESIGN_W, h / (double) DESIGN_H);
            Color primary = accent.isEmpty() ? new Color(0x3C58E8) : Color.decode(accent);
            Color text = light ? new Color(0x252730) : new Color(0xE8EBF1);
            Color muted = light ? new Color(0x5C6270) : new Color(0xABB1BF);
            Color line = light ? new Color(0xD7DAE2) : new Color(0x3B3D49);
            if (light) {
                g.setColor(new Color(0xF5F6FA));
                g.fill(new RoundRectangle2D.Float(0, 0, DESIGN_W, DESIGN_H, 9, 9));
            } else {
                OrionGlassStyle.drawPanel(g, 0, 0, DESIGN_W, DESIGN_H, 9);
            }
            g.setColor(line);
            g.setStroke(new BasicStroke(.65F));
            g.draw(new RoundRectangle2D.Float(.5F, .5F, DESIGN_W - 1, DESIGN_H - 1, 9, 9));

            g.setColor(new Color(primary.getRed(), primary.getGreen(), primary.getBlue(), 42));
            g.fill(new RoundRectangle2D.Float(13, 12, 24, 24, 6, 6));
            centered(g, "!", 25, 28, OrionInterfaceFonts.face(3, 14), light ? text : Color.WHITE);
            draw(g, "PvpSafe", 46, 20, OrionInterfaceFonts.face(2, 8), muted);
            draw(g, tr("Выйти во время боя?"), 46, 34, OrionInterfaceFonts.face(3, 12), text);
            g.setColor(line);
            g.drawLine(13, 45, DESIGN_W - 13, 45);
            Font body = OrionInterfaceFonts.face(1, 9), button = OrionInterfaceFonts.face(2, 9);
            String status = remaining > 0 ? tr("До конца таймера") : tr("Боевой таймер закончился");
            draw(g, status, 13, 62, body, muted);
            if (remaining > 0) {
                String time = String.format(java.util.Locale.ROOT, "%d:%02d", remaining / 60, remaining % 60);
                draw(g, time, DESIGN_W - 13 - width(g, button, time), 62, button, text);
            }
            g.setColor(hovered == 1 ? primary.brighter() : primary);
            g.fill(new RoundRectangle2D.Float(13, BUTTON_Y, 115, BUTTON_H, 5, 5));
            g.setColor(hovered == 2 ? line : new Color(line.getRed(), line.getGreen(), line.getBlue(), 96));
            g.fill(new RoundRectangle2D.Float(136, BUTTON_Y, 115, BUTTON_H, 5, 5));
            double luminance = .2126 * primary.getRed() + .7152 * primary.getGreen() + .0722 * primary.getBlue();
            centered(g, tr("Остаться"), 70.5F, 91.5F, button, luminance > 155 ? new Color(0x20222A) : Color.WHITE);
            centered(g, tr("Выйти"), 193.5F, 91.5F, button, text);
        } finally { g.dispose(); }
        if (texture == null || pixelW != w || pixelH != h) {
            Minecraft.getInstance().getTextureManager().release(TEXTURE);
            NativeImage pixels = new NativeImage(w,h,true); OrionHudRenderer.copyPixels(image,pixels);
            texture = new DynamicTexture(() -> "orion_pvp_exit",pixels);
            Minecraft.getInstance().getTextureManager().register(TEXTURE,texture); pixelW=w; pixelH=h;
        } else if (texture.getPixels() != null && !texture.getPixels().isClosed()) { OrionHudRenderer.copyPixels(image,texture.getPixels()); texture.upload(); }
    }
    private static float width(Graphics2D g, Font font, String text) {
        Font f=font.deriveFont(font.getSize2D()*16F); return (float)g.getFontMetrics(f).getStringBounds(text,g).getWidth()/16F;
    }
    private static void draw(Graphics2D g,String s,float x,float y,Font f,Color c) { g.setFont(f); g.setColor(c); g.drawString(s,x,y); }
    private static void centered(Graphics2D g,String s,float x,float y,Font f,Color color) { draw(g,s,x-width(g,f,s)/2F,y,f,color); }
    private static String tr(String text) { return OrionGuiLanguage.translate(text); }
    private int actionAt(double mouseX, double mouseY) {
        if (guiW == 0 || guiH == 0) return 0;
        double px = (mouseX - x()) * DESIGN_W / guiW, py = (mouseY - y()) * DESIGN_H / guiH;
        if (py < BUTTON_Y || py > BUTTON_Y + BUTTON_H) return 0;
        if (px >= 13 && px <= 128) return 1;
        return px >= 136 && px <= 251 ? 2 : 0;
    }
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT || guiW == 0 || guiH == 0) return super.mouseClicked(event,doubleClick);
        int action = actionAt(event.x(), event.y());
        if (action == 1) minecraft.gui.setScreen(previous);
        if (action == 2) minecraft.disconnectFromWorld(ClientLevel.DEFAULT_QUIT_MESSAGE);
        return true;
    }
    @Override public boolean keyPressed(KeyEvent event) {
        if(event.key()==InputConstants.KEY_ESCAPE) { minecraft.gui.setScreen(previous); return true; }
        return super.keyPressed(event);
    }
    @Override public void removed() {
        Minecraft.getInstance().getTextureManager().release(TEXTURE); texture=null; fingerprint=null;
        super.removed();
    }
}
