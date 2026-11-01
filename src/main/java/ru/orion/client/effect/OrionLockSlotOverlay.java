package ru.orion.client.effect;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import ru.orion.client.OrionClient;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Line2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/** Один Java2D-слой над помеченными слотами открытого инвентаря. */
public final class OrionLockSlotOverlay {
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, "lock_slot_overlay");
    private DynamicTexture texture;
    private List<Object> fingerprint;
    private int pixelW, pixelH, guiW, guiH;
    public void draw(GuiGraphicsExtractor context, AbstractContainerMenu menu, int left, int top) {
        var config = OrionClient.getConfigOrNull();
        if (config == null || !config.isLockSlotEnabled()) { release(); return; }
        List<Slot> slots = new ArrayList<>();
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        List<Object> fp = new ArrayList<>();
        int scale = Math.max(1, Math.min(6, Minecraft.getInstance().getWindow().getGuiScale()));
        fp.add(scale);
        for (Slot slot : menu.slots) {
            if (!slot.isActive() || !OrionLockSlot.isLocked(slot)) continue;
            slots.add(slot); minX = Math.min(minX, slot.x); minY = Math.min(minY, slot.y);
            maxX = Math.max(maxX, slot.x + 16); maxY = Math.max(maxY, slot.y + 16);
            fp.add(slot.x); fp.add(slot.y);
        }
        if (slots.isEmpty()) { release(); return; }
        fp.add(maxX-minX); fp.add(maxY-minY);
        guiW = maxX-minX; guiH = maxY-minY;
        if (!fp.equals(fingerprint)) { update(slots,minX,minY,scale); fingerprint=List.copyOf(fp); }
        if (texture != null) context.blit(TEXTURE,left+minX,top+minY,left+minX+guiW,top+minY+guiH,0,1,0,1);
    }
    private void update(List<Slot> slots,int minX,int minY,int scale) {
        BufferedImage image=new BufferedImage(guiW*scale,guiH*scale,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,RenderingHints.VALUE_STROKE_PURE);
            g.scale(scale,scale);
            for (Slot slot:slots) {
                float x=slot.x-minX,y=slot.y-minY;
                g.setColor(new Color(220,45,45,85)); g.fill(new RoundRectangle2D.Float(x,y,16,16,3,3));
                g.setColor(new Color(255,90,80,190)); g.setStroke(new BasicStroke(0.8F));
                g.draw(new RoundRectangle2D.Float(x+.4F,y+.4F,15.2F,15.2F,3,3));
                g.setColor(new Color(20,15,18,210)); g.fill(new RoundRectangle2D.Float(x+9,y+1,6,7,2,2));
                g.setColor(Color.WHITE); g.setStroke(new BasicStroke(.8F,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
                g.draw(new RoundRectangle2D.Float(x+10.5F,y+2F,3,3.5F,2.5F,2.5F));
                g.fill(new RoundRectangle2D.Float(x+10,y+4,4,3,1,1));
                g.setColor(new Color(40,30,30)); g.draw(new Line2D.Float(x+12,y+5,x+12,y+6));
            }
        } finally { g.dispose(); }
        int w=image.getWidth(),h=image.getHeight(); Minecraft client=Minecraft.getInstance();
        if(texture==null || pixelW!=w || pixelH!=h) {
            client.getTextureManager().release(TEXTURE); NativeImage pixels=new NativeImage(w,h,true); copy(image,pixels);
            texture=new DynamicTexture(() -> "orion_lock_slots",pixels); client.getTextureManager().register(TEXTURE,texture); pixelW=w;pixelH=h;
        } else if(texture.getPixels()!=null && !texture.getPixels().isClosed()) { copy(image,texture.getPixels());texture.upload(); }
    }
    private static void copy(BufferedImage image,NativeImage pixels) {
        int w=image.getWidth(),h=image.getHeight(); int[] data=image.getRGB(0,0,w,h,null,0,w);
        for(int y=0;y<h;y++)for(int x=0;x<w;x++)pixels.setPixel(x,y,data[y*w+x]);
    }
    public void release() {
        if(texture!=null) Minecraft.getInstance().getTextureManager().release(TEXTURE);
        texture=null; fingerprint=null;
    }
}
