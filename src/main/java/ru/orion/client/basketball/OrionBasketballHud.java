package ru.orion.client.basketball;

import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import ru.orion.client.OrionClient;
import ru.orion.client.config.OrionConfigManager;
import ru.orion.client.hud.OrionGlassCompositor;
import ru.orion.client.hud.OrionGlassStyle;
import ru.orion.client.gui.OrionGuiLanguage;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.List;

/** Круг заряда рядом с прицелом и счёт без перерисовки неподвижных кадров. */
public final class OrionBasketballHud {
    private static final Identifier TEXTURE=Identifier.fromNamespaceAndPath(OrionClient.MOD_ID,"basketball_hud");
    private final OrionBasketball game;private final OrionConfigManager manager;
    private DynamicTexture texture;private int texW,texH,guiW,guiH;private List<Object> last;
    public OrionBasketballHud(OrionBasketball game,OrionConfigManager manager){this.game=game;this.manager=manager;}
    public void register(){HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(OrionClient.MOD_ID,"basketball"),this::render);}
    private void render(GuiGraphicsExtractor context,DeltaTracker delta){
        Minecraft client=Minecraft.getInstance();var cfg=manager.getConfig();
        if(!cfg.isBasketballEnabled()||!cfg.isBasketballHud()||client.player==null){release();return;}
        int scale=Math.max(1,Math.min(6,client.getWindow().getGuiScale()));
        int phase=Math.round(game.indicatorCharge()*100);
        int alpha=Math.round(game.indicatorAlpha()*255);
        boolean scored=game.scoreAt()!=0&&System.nanoTime()-game.scoreAt()<1_500_000_000L;
        var match=game.match();
        List<Object> fp=List.of(scale,phase,alpha,game.score(),scored,match.active(),match.red(),match.blue(),match.status(),OrionGuiLanguage.english());
        if(!fp.equals(last)){update(scale,phase,alpha,game.score(),scored);last=fp;}
        if(texture!=null){int x=(context.guiWidth()-guiW)/2,y=(context.guiHeight()-guiH)/2;
            if(!OrionGlassCompositor.INSTANCE.submit(TEXTURE,context,texture,x,y,guiW,guiH))context.blit(TEXTURE,x,y,x+guiW,y+guiH,0,1,0,1);}
    }
    private void update(int scale,int phase,int alpha,int score,boolean scored){
        guiW=180;guiH=102;int w=guiW*scale,h=guiH*scale;BufferedImage image=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);Graphics2D g=image.createGraphics();
        try{g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,RenderingHints.VALUE_FRACTIONALMETRICS_ON);g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,RenderingHints.VALUE_STROKE_PURE);g.setRenderingHint(RenderingHints.KEY_RENDERING,RenderingHints.VALUE_RENDER_QUALITY);g.scale(scale,scale);
            if(alpha>0){g.setStroke(new BasicStroke(2.3F,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));g.setColor(new Color(10,10,12,Math.round(alpha*.51F)));g.draw(new Arc2D.Float(75,36,30,30,0,360,Arc2D.OPEN));g.setColor(new Color(255,Math.round(190-phase*.95F),35,Math.round(alpha*.94F)));g.draw(new Arc2D.Float(75,36,30,30,90,-phase*3.6F,Arc2D.OPEN));}
            var match=game.match();
            g.setFont(new Font(Font.SANS_SERIF,Font.BOLD,9).deriveFont(9F));
            String text=match.active()?OrionGuiLanguage.translate("Красные")+" "+match.red()+" : "+match.blue()+" "+OrionGuiLanguage.translate("Синие")
                    :scored?"SCORE!  "+score:(OrionGuiLanguage.english()?"Score: ":"Счёт: ")+score;
            String detail=match.active()?OrionGuiLanguage.translate(match.status()):"";
            Font f=g.getFont().deriveFont(144F);
            float tw=(float)g.getFontMetrics(f).getStringBounds(text,g).getWidth()/16F;
            float dw=(float)g.getFontMetrics(f).getStringBounds(detail,g).getWidth()/16F;
            float pw=Math.max(tw,dw)+10, ph=match.active()?28:15, x=90-pw/2;
            OrionGlassStyle.drawPanel(g,x,3,pw,ph,8);g.setColor(scored?new Color(255,166,59):Color.WHITE);g.drawString(text,90-tw/2,14F);
            if(match.active())g.drawString(detail,90-dw/2,25F);
            OrionGlassCompositor.INSTANCE.updateMask(TEXTURE,new OrionGlassCompositor.MaskLayout(w,h,guiW,guiH,List.of(new OrionGlassCompositor.Panel(x,3,pw,ph,8))));
        }finally{g.dispose();}
        Minecraft client=Minecraft.getInstance();if(texture==null||texW!=w||texH!=h){client.getTextureManager().release(TEXTURE);NativeImage pixels=new NativeImage(w,h,true);copy(image,pixels);texture=new DynamicTexture(()->"orion_basketball_hud",pixels);client.getTextureManager().register(TEXTURE,texture);texW=w;texH=h;}
        else if(texture.getPixels()!=null&&!texture.getPixels().isClosed()){copy(image,texture.getPixels());texture.upload();}
    }
    private static void copy(BufferedImage image,NativeImage pixels){int w=image.getWidth(),h=image.getHeight();int[] data=image.getRGB(0,0,w,h,null,0,w);for(int y=0;y<h;y++)for(int x=0;x<w;x++)pixels.setPixel(x,y,data[y*w+x]);}
    private void release(){if(texture!=null)Minecraft.getInstance().getTextureManager().release(TEXTURE);OrionGlassCompositor.INSTANCE.releaseMask(TEXTURE);texture=null;last=null;}
}
