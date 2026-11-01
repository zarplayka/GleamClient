package ru.orion.client.hud;

import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import ru.orion.client.OrionClient;
import ru.orion.client.config.OrionConfigManager;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.List;

/** Фон Java2D и реальные модели предметов из основного инвентаря. */
public final class OrionInventoryHud {
    private static final Identifier TEXTURE=Identifier.fromNamespaceAndPath(OrionClient.MOD_ID,"inventory_hud");
    private static final int COLS=9, ROWS=3, CELL=19, DESIGN_W=COLS*CELL, DESIGN_H=ROWS*CELL;
    private final OrionConfigManager configManager;
    private final OrionHudDrag drag=new OrionHudDrag("inventory");
    private DynamicTexture texture;
    private int pixelW,pixelH,guiW,guiH;
    private List<Object> fingerprint;
    public OrionInventoryHud(OrionConfigManager configManager){this.configManager=configManager;}
    public void register(){HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(OrionClient.MOD_ID,"inventory_hud_element"),this::render);}
    private void render(GuiGraphicsExtractor context,DeltaTracker ticks) {
        Minecraft client=Minecraft.getInstance();var config=configManager.getConfig();
        if(!config.isInventoryHudEnabled()||client.player==null||client.level==null){release();return;}
        int scale=Math.max(1,Math.min(6,client.getWindow().getGuiScale()));
        float elementScale=config.getHudScale()*config.getHudElementScale("inventory");
        List<Object> next=List.of(scale,elementScale,context.guiWidth(),context.guiHeight());
        if(!next.equals(fingerprint)){update(scale,elementScale,context.guiWidth(),context.guiHeight());fingerprint=next;}
        if(texture==null)return;
        int x=config.getInventoryHudX()<0?context.guiWidth()-guiW-8:config.getInventoryHudX();
        int y=config.getInventoryHudY()<0?(context.guiHeight()-guiH)/2:config.getInventoryHudY();
        x=Math.max(0,Math.min(x,context.guiWidth()-guiW));y=Math.max(0,Math.min(y,context.guiHeight()-guiH));
        var position=drag.update(context,x,y,guiW,guiH,configManager,(nx,ny)->{config.setInventoryHudX(nx);config.setInventoryHudY(ny);});
        if(!OrionGlassCompositor.INSTANCE.submit(TEXTURE,context,texture,position.x(),position.y(),guiW,guiH))
            context.blit(TEXTURE,position.x(),position.y(),position.x()+guiW,position.y()+guiH,0,1,0,1);
        var pose=context.pose();pose.pushMatrix();
        pose.translate(position.x(),position.y());pose.scale(guiW/(float)DESIGN_W,guiH/(float)DESIGN_H);
        try {
            for(int i=0;i<COLS*ROWS;i++) {
                ItemStack item=client.player.getInventory().getItem(i+9);
                if(item.isEmpty())continue;
                int ix=(i%COLS)*CELL+1,iy=(i/COLS)*CELL+1;
                context.item(item,ix,iy);
                context.itemDecorations(client.font,item,ix,iy);
            }
        } finally {pose.popMatrix();}
    }
    private void update(int scale,float hudScale,int screenW,int screenH) {
        float fit=Math.min(hudScale,Math.min(Math.max(1,screenW-8)/(float)DESIGN_W,Math.max(1,screenH-8)/(float)DESIGN_H));
        guiW=Math.max(1,Math.round(DESIGN_W*fit));guiH=Math.max(1,Math.round(DESIGN_H*fit));
        BufferedImage image=new BufferedImage(guiW*scale,guiH*scale,BufferedImage.TYPE_INT_ARGB);Graphics2D g=image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            g.setRenderingHint(RenderingHints.KEY_RENDERING,RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,RenderingHints.VALUE_STROKE_PURE);
            g.scale(image.getWidth()/(double)DESIGN_W,image.getHeight()/(double)DESIGN_H);
            for(int i=0;i<COLS*ROWS;i++) {float x=(i%COLS)*CELL,y=(i/COLS)*CELL;
                OrionGlassStyle.drawPanel(g,x+.5F,y+.5F,18,18,4);}
        } finally {g.dispose();}
        upload(image);
        var panels=new java.util.ArrayList<OrionGlassCompositor.Panel>();
        for(int i=0;i<COLS*ROWS;i++)panels.add(new OrionGlassCompositor.Panel((i%COLS)*CELL+.5F,(i/COLS)*CELL+.5F,18,18,4));
        OrionGlassCompositor.INSTANCE.updateMask(TEXTURE,new OrionGlassCompositor.MaskLayout(image.getWidth(),image.getHeight(),DESIGN_W,DESIGN_H,panels));
    }
    private void upload(BufferedImage image){Minecraft client=Minecraft.getInstance();int w=image.getWidth(),h=image.getHeight();
        if(texture==null||pixelW!=w||pixelH!=h){client.getTextureManager().release(TEXTURE);NativeImage pixels=new NativeImage(w,h,true);OrionHudRenderer.copyPixels(image,pixels);texture=new DynamicTexture(()->"orion_inventory_hud",pixels);client.getTextureManager().register(TEXTURE,texture);pixelW=w;pixelH=h;}
        else if(texture.getPixels()!=null&&!texture.getPixels().isClosed()){OrionHudRenderer.copyPixels(image,texture.getPixels());texture.upload();}}
    private void release(){drag.cancel(configManager);if(texture!=null)Minecraft.getInstance().getTextureManager().release(TEXTURE);OrionGlassCompositor.INSTANCE.releaseMask(TEXTURE);texture=null;fingerprint=null;}
}
