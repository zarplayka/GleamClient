package ru.orion.client.football;

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
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.List;

public final class OrionFootballHud {
    private static final Identifier TEXTURE=Identifier.fromNamespaceAndPath(OrionClient.MOD_ID,"football_hud");
    private final OrionFootball football;private final OrionConfigManager manager;
    private DynamicTexture texture;private int pixelWidth,pixelHeight;private List<Object> last;
    public OrionFootballHud(OrionFootball football,OrionConfigManager manager){this.football=football;this.manager=manager;}
    public void register(){HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(OrionClient.MOD_ID,"football_hud_element"),this::render);}
    private void render(GuiGraphicsExtractor context,DeltaTracker delta){
        Minecraft client=Minecraft.getInstance();var config=manager.getConfig();
        if(!config.isFootballEnabled()||client.player==null){release();return;}
        int scale=Math.max(1,Math.min(6,client.getWindow().getGuiScale()));int power=football.charging()?Math.round(football.charge()*100):0;
        boolean goal=football.goalAt()!=0&&System.nanoTime()-football.goalAt()<1_500_000_000L;
        List<Object> fp=List.of(scale,power,football.charging(),goal,football.shots(),football.goals(),football.saves(),football.misses(),config.isFootballPenaltyMode());
        if(!fp.equals(last)){update(scale,power,goal,config.isFootballPenaltyMode());last=fp;}
        if(texture!=null){int x=(context.guiWidth()-160)/2,y=(context.guiHeight()-112)/2;
            if(!OrionGlassCompositor.INSTANCE.submit(TEXTURE,context,texture,x,y,160,112))context.blit(TEXTURE,x,y,x+160,y+112,0,1,0,1);}
    }
    private void update(int scale,int power,boolean goal,boolean penalty){
        int w=160*scale,h=112*scale;BufferedImage image=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);Graphics2D g=image.createGraphics();
        try{g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,RenderingHints.VALUE_FRACTIONALMETRICS_ON);g.setRenderingHint(RenderingHints.KEY_RENDERING,RenderingHints.VALUE_RENDER_QUALITY);g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,RenderingHints.VALUE_STROKE_PURE);g.scale(scale,scale);
            if(football.charging()){g.setStroke(new BasicStroke(2.3F,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));g.setColor(new Color(10,10,12,140));g.draw(new Arc2D.Float(65,41,30,30,0,360,Arc2D.OPEN));g.setColor(new Color(255,Math.round(185-power*.8F),35,240));g.draw(new Arc2D.Float(65,41,30,30,90,-power*3.6F,Arc2D.OPEN));}
            Font font=new Font(Font.SANS_SERIF,Font.BOLD,8).deriveFont(8F);g.setFont(font);
            int percentage=football.shots()==0?0:Math.round(football.goals()*100F/football.shots());
            String text=goal?"GOAL!  "+football.goals():penalty?"Удары "+football.shots()+"  Голы "+football.goals()+"  Сейвы "+football.saves()+"  Мимо "+football.misses()+"  "+percentage+"%":"Голы: "+football.goals();
            Font measuring=font.deriveFont(128F);float tw=(float)g.getFontMetrics(measuring).getStringBounds(text,g).getWidth()/16F;
            float x=80-tw/2;OrionGlassStyle.drawPanel(g,x-5,3,tw+10,15,8);g.setColor(goal?new Color(255,166,59):Color.WHITE);g.drawString(text,x,14F);
            OrionGlassCompositor.INSTANCE.updateMask(TEXTURE,new OrionGlassCompositor.MaskLayout(w,h,160,112,List.of(new OrionGlassCompositor.Panel(x-5,3,tw+10,15,8))));
        }finally{g.dispose();}
        Minecraft client=Minecraft.getInstance();if(texture==null||pixelWidth!=w||pixelHeight!=h){client.getTextureManager().release(TEXTURE);NativeImage pixels=new NativeImage(w,h,true);copy(image,pixels);texture=new DynamicTexture(()->"orion_football_hud",pixels);client.getTextureManager().register(TEXTURE,texture);pixelWidth=w;pixelHeight=h;}else if(texture.getPixels()!=null&&!texture.getPixels().isClosed()){copy(image,texture.getPixels());texture.upload();}
    }
    private static void copy(BufferedImage image,NativeImage pixels){int w=image.getWidth(),h=image.getHeight();int[] data=image.getRGB(0,0,w,h,null,0,w);for(int y=0;y<h;y++)for(int x=0;x<w;x++)pixels.setPixel(x,y,data[y*w+x]);}
    private void release(){if(texture!=null)Minecraft.getInstance().getTextureManager().release(TEXTURE);OrionGlassCompositor.INSTANCE.releaseMask(TEXTURE);texture=null;last=null;}
}
