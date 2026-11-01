package ru.orion.client.hud;

import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import ru.orion.client.OrionClient;
import ru.orion.client.config.OrionConfigManager;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.List;

/** Красные метки и замочки поверх защищённых слотов хотбара. */
public final class OrionHotbarLocks {
    private static final Identifier TEXTURE=Identifier.fromNamespaceAndPath(OrionClient.MOD_ID,"hotbar_locks");
    private final OrionConfigManager configManager;
    private DynamicTexture texture;
    private List<Object> last;
    private int width,height;
    public OrionHotbarLocks(OrionConfigManager configManager){this.configManager=configManager;}
    public void register(){HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR,Identifier.fromNamespaceAndPath(OrionClient.MOD_ID,"hotbar_locks_element"),this::render);}
    private void render(GuiGraphicsExtractor context,DeltaTracker delta) {
        Minecraft client=Minecraft.getInstance();var config=configManager.getConfig();
        if(client.player==null || !config.isLockSlotEnabled() || client.player.isSpectator()){release();return;}
        long bits=config.getLockSlotMask() & 0x1FFL;
        if(bits==0){release();return;}
        int scale=Math.max(1,Math.min(6,client.getWindow().getGuiScale()));List<Object> fp=List.of(bits,scale);
        if(!fp.equals(last)){update(bits,scale);last=fp;}
        if(texture!=null){int x=context.guiWidth()/2-90,y=context.guiHeight()-21;context.blit(TEXTURE,x,y,x+180,y+20,0,1,0,1);}
    }
    private void update(long bits,int scale){
        BufferedImage image=new BufferedImage(180*scale,20*scale,BufferedImage.TYPE_INT_ARGB);Graphics2D g=image.createGraphics();
        try{g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,RenderingHints.VALUE_STROKE_PURE);g.scale(scale,scale);
            for(int i=0;i<9;i++)if((bits&(1L<<i))!=0){float x=i*20+2;
                g.setColor(new Color(225,46,46,75));g.fill(new RoundRectangle2D.Float(x,2,16,16,3,3));
                g.setColor(new Color(255,88,78,180));g.setStroke(new BasicStroke(.8F));g.draw(new RoundRectangle2D.Float(x+.4F,2.4F,15.2F,15.2F,3,3));
                g.setColor(new Color(18,14,16,210));g.fill(new RoundRectangle2D.Float(x+9,3,6,7,2,2));
                g.setColor(Color.WHITE);g.setStroke(new BasicStroke(.8F));g.draw(new RoundRectangle2D.Float(x+10.5F,4,3,3.5F,2.5F,2.5F));g.fill(new RoundRectangle2D.Float(x+10,6,4,3,1,1));
            }
        }finally{g.dispose();}
        Minecraft client=Minecraft.getInstance();int w=image.getWidth(),h=image.getHeight();
        if(texture==null||width!=w||height!=h){client.getTextureManager().release(TEXTURE);NativeImage pixels=new NativeImage(w,h,true);OrionHudRenderer.copyPixels(image,pixels);texture=new DynamicTexture(()->"orion_hotbar_locks",pixels);client.getTextureManager().register(TEXTURE,texture);width=w;height=h;}
        else if(texture.getPixels()!=null&&!texture.getPixels().isClosed()){OrionHudRenderer.copyPixels(image,texture.getPixels());texture.upload();}
    }
    private void release(){if(texture!=null)Minecraft.getInstance().getTextureManager().release(TEXTURE);texture=null;last=null;}
}
