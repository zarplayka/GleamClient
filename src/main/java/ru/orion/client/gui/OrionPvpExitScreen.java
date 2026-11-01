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
    private static final int DESIGN_W = 240, DESIGN_H = 90;
    private final Screen previous;
    private DynamicTexture texture;
    private int guiW, guiH, pixelW, pixelH;
    private List<Object> fingerprint;
    public OrionPvpExitScreen(Screen previous) { super(Component.literal("Выход во время боя")); this.previous = previous; }
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
        List<Object> next = List.of(scale, guiW, guiH, remaining);
        if (!next.equals(fingerprint)) { update(scale, remaining); fingerprint = next; }
        if (texture != null) context.blit(TEXTURE, x(), y(), x() + guiW, y() + guiH, 0, 1, 0, 1);
    }
    private int x() { return (width - guiW) / 2; }
    private int y() { return (height - guiH) / 2; }
    private void update(int scale, int remaining) {
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
            g.setColor(new Color(17,18,23,245)); g.fill(new RoundRectangle2D.Float(0,0,DESIGN_W,DESIGN_H,12,12));
            g.setColor(new Color(80,80,90,190)); g.setStroke(new BasicStroke(1));
            g.draw(new RoundRectangle2D.Float(.5F,.5F,DESIGN_W-1,DESIGN_H-1,12,12));
            Font title = font(10F, Font.BOLD), body = font(7.3F, Font.PLAIN), button = font(8F, Font.BOLD);
            draw(g, "Выйти во время боя?", 14, 22, title, new Color(255, 142, 48));
            draw(g, remaining > 0 ? "До конца таймера: " + remaining + " с" : "Боевой таймер закончился", 14, 36, body, new Color(205,205,215));
            g.setColor(new Color(42,43,50)); g.fill(new RoundRectangle2D.Float(14,50,101,25,8,8));
            g.setColor(new Color(213,83,39)); g.fill(new RoundRectangle2D.Float(125,50,101,25,8,8));
            centered(g, "Остаться", 64.5F, 66.5F, button); centered(g, "Выйти", 175.5F, 66.5F, button);
        } finally { g.dispose(); }
        if (texture == null || pixelW != w || pixelH != h) {
            Minecraft.getInstance().getTextureManager().release(TEXTURE);
            NativeImage pixels = new NativeImage(w,h,true); copy(image,pixels);
            texture = new DynamicTexture(() -> "orion_pvp_exit",pixels);
            Minecraft.getInstance().getTextureManager().register(TEXTURE,texture); pixelW=w; pixelH=h;
        } else if (texture.getPixels() != null && !texture.getPixels().isClosed()) { copy(image,texture.getPixels()); texture.upload(); }
    }
    private static void copy(BufferedImage image, NativeImage pixels) {
        int w=image.getWidth(),h=image.getHeight(); int[] values=image.getRGB(0,0,w,h,null,0,w);
        for(int y=0;y<h;y++) for(int x=0;x<w;x++) pixels.setPixel(x,y,values[y*w+x]);
    }
    private static Font font(float size, int style) {
        for (String family : new String[]{"Inter", "Segoe UI"}) { Font f = new Font(family,style,Math.round(size)); if(f.getFamily().equalsIgnoreCase(family) && f.canDisplay('Я') && f.canDisplay('ё')) return f.deriveFont(size); }
        return new Font(Font.SANS_SERIF,style,Math.round(size)).deriveFont(size);
    }
    private static float width(Graphics2D g, Font font, String text) {
        Font f=font.deriveFont(font.getSize2D()*16F); return (float)g.getFontMetrics(f).getStringBounds(text,g).getWidth()/16F;
    }
    private static void draw(Graphics2D g,String s,float x,float y,Font f,Color c) { g.setFont(f); g.setColor(c); g.drawString(s,x,y); }
    private static void centered(Graphics2D g,String s,float x,float y,Font f) { draw(g,s,x-width(g,f,s)/2F,y,f,Color.WHITE); }
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT || guiW == 0 || guiH == 0) return super.mouseClicked(event,doubleClick);
        double px=(event.x()-x())*DESIGN_W/guiW, py=(event.y()-y())*DESIGN_H/guiH;
        if (py>=50 && py<=75 && px>=14 && px<=115) { minecraft.gui.setScreen(previous); return true; }
        if (py>=50 && py<=75 && px>=125 && px<=226) { minecraft.disconnectFromWorld(ClientLevel.DEFAULT_QUIT_MESSAGE); return true; }
        return true;
    }
    @Override public boolean keyPressed(KeyEvent event) {
        if(event.key()==InputConstants.KEY_ESCAPE) { minecraft.gui.setScreen(previous); return true; }
        return super.keyPressed(event);
    }
    @Override public void removed() {
        Minecraft.getInstance().getTextureManager().release(TEXTURE); texture=null; fingerprint=null;
        OrionClient.getInstance().getConfigManager().save(); super.removed();
    }
}
