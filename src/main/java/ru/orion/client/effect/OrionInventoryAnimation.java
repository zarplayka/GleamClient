package ru.orion.client.effect;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.Slot;
import ru.orion.client.OrionClient;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

/** Плавная рамка указателя инвентаря без изменений зон клика. */
public final class OrionInventoryAnimation {
    private static final Identifier TEXTURE=Identifier.fromNamespaceAndPath(OrionClient.MOD_ID,"inventory_animation");
    private DynamicTexture texture;
    private int textureScale;
    private float x=Float.NaN,y;
    private long lastNanos;
    public void draw(GuiGraphicsExtractor context,Slot slot,int left,int top) {
        var config=OrionClient.getConfigOrNull();
        if(config==null || !config.isAnimationsEnabled()) { release();x=Float.NaN;lastNanos=0;return; }
        if(slot==null || !slot.isActive()) { x=Float.NaN;lastNanos=0;return; }
        long now=System.nanoTime();float targetX=left+slot.x-1,targetY=top+slot.y-1;
        if(!Float.isFinite(x)||lastNanos==0) { x=targetX;y=targetY; }
        else {
            float dt=Math.min(.1F,Math.max(0,(now-lastNanos)/1_000_000_000F));
            float factor=1F-(float)Math.exp(-dt*4000F/config.getAnimationsDuration());
            x+=(targetX-x)*factor;y+=(targetY-y)*factor;
        }
        lastNanos=now;
        int scale=Math.max(1,Math.min(6,Minecraft.getInstance().getWindow().getGuiScale()));
        if(texture==null||textureScale!=scale)create(scale);
        int px=Math.round(x),py=Math.round(y);
        context.blit(TEXTURE,px,py,px+18,py+18,0,1,0,1);
    }
    private void create(int scale) {
        release();BufferedImage image=new BufferedImage(18*scale,18*scale,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,RenderingHints.VALUE_STROKE_PURE);
            g.scale(scale,scale);g.setStroke(new BasicStroke(1F));g.setColor(new Color(255,153,56,180));
            g.draw(new RoundRectangle2D.Float(.5F,.5F,17,17,4,4));
        } finally {g.dispose();}
        NativeImage pixels=new NativeImage(image.getWidth(),image.getHeight(),true);
        int w=image.getWidth(),h=image.getHeight();int[] data=image.getRGB(0,0,w,h,null,0,w);
        for(int iy=0;iy<h;iy++)for(int ix=0;ix<w;ix++)pixels.setPixel(ix,iy,data[iy*w+ix]);
        texture=new DynamicTexture(()->"orion_inventory_animation",pixels);
        Minecraft.getInstance().getTextureManager().register(TEXTURE,texture);textureScale=scale;
    }
    public void release() {
        if(texture!=null)Minecraft.getInstance().getTextureManager().release(TEXTURE);
        texture=null;textureScale=0;
    }
}
