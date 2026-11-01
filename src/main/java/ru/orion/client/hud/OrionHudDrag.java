package ru.orion.client.hud;

import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.platform.InputConstants;
import ru.orion.client.mixin.OrionMouseHandlerAccessor;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.util.Mth;
import ru.orion.client.config.OrionConfigManager;

import java.util.function.BiConsumer;
import java.util.LinkedHashMap;
import java.util.Map;
import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import ru.orion.client.OrionClient;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;

/** Захват одного HUD-элемента при перетаскивании в открытом чате. */
public final class OrionHudDrag {
    private final String id;
    public OrionHudDrag(String id) { this.id = id; }
    private static final int HANDLE_SIZE = 13;
    private static final Identifier[] HANDLES = {
            Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, "hud_resize_idle"),
            Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, "hud_resize_active")};
    private static int handleScale;
    private static OrionHudDrag owner;
    private static final Map<OrionHudDrag, Bounds> REGIONS = new LinkedHashMap<>();
    private record Bounds(int x, int y, int w, int h, long seen) {
        boolean contains(double mx, double my) {
            return System.nanoTime() - seen < 500_000_000L && mx >= x && mx <= x+w && my >= y && my <= y+h;
        }
    }
    private boolean wasDown, dragging, moved, justDragged, resizing;
    private double offsetX, offsetY, pressX, pressY;
    private int initialWidth, initialHeight, anchorX, anchorY;
    private float initialScale;
    public record Position(int x,int y) { }
    public Position update(GuiGraphicsExtractor context,int x,int y,int w,int h,OrionConfigManager manager,BiConsumer<Integer,Integer> setPosition) {
        return update(context, x, y, w, h, manager, setPosition, true);
    }
    public Position update(GuiGraphicsExtractor context,int x,int y,int w,int h,OrionConfigManager manager,BiConsumer<Integer,Integer> setPosition,boolean canStart) {
        Minecraft client=Minecraft.getInstance();
        boolean open=client.gui.screen() instanceof ChatScreen;
        boolean down=open && isLeftDown(client);
        double mx=open?client.mouseHandler.getScaledXPos(client.getWindow()):-1;
        double my=open?client.mouseHandler.getScaledYPos(client.getWindow()):-1;
        justDragged=false;
        REGIONS.put(this,new Bounds(x,y,w,h,System.nanoTime()));
        boolean onHandle=mx>=x+w-HANDLE_SIZE && mx<=x+w && my>=y+h-HANDLE_SIZE && my<=y+h;
        if(down && !wasDown && (canStart || onHandle) && owner==null && isTopmost(mx,my)) {
            owner=this;dragging=true;moved=false;offsetX=mx-x;offsetY=my-y;pressX=mx;pressY=my;
            resizing=onHandle; initialWidth=w;initialHeight=h;anchorX=x;anchorY=y;
            initialScale=manager.getConfig().getHudElementScale(id);
            if(resizing)setPosition.accept(x,y);
        } else if(down && dragging && owner==this) {
            if(Math.abs(mx-pressX)+Math.abs(my-pressY)>=3) moved=true;
            if(moved) {
                if(resizing) {
                    manager.getConfig().setHudElementScale(id, OrionHudResize.scale(initialScale,
                            initialWidth,initialHeight,mx-pressX,my-pressY,
                            context.guiWidth()-anchorX,context.guiHeight()-anchorY));
                    x=anchorX;y=anchorY;
                } else {
                    x=Mth.clamp((int)Math.round(mx-offsetX),0,Math.max(0,context.guiWidth()-w));
                    y=Mth.clamp((int)Math.round(my-offsetY),0,Math.max(0,context.guiHeight()-h));
                }
                setPosition.accept(x,y);
            }
        } else if(!down && dragging) {
            justDragged=moved;
            if(moved)manager.save();
            dragging=false;resizing=false;moved=false;if(owner==this)owner=null;
        }
        wasDown=down;
        REGIONS.put(this,new Bounds(x,y,w,h,System.nanoTime()));
        return new Position(x,y);
    }
    public static boolean isLeftDown(Minecraft client) {
        var button=((OrionMouseHandlerAccessor)(Object)client.mouseHandler).orion$getActiveButton();
        return button!=null && button.button()==InputConstants.MOUSE_BUTTON_LEFT;
    }
    public static boolean covers(double x,double y) {
        for(Bounds bounds:REGIONS.values())if(bounds.contains(x,y))return true;
        return false;
    }
    private boolean isTopmost(double x,double y) {
        OrionHudDrag top=null;
        for(var region:REGIONS.entrySet())if(region.getValue().contains(x,y))top=region.getKey();
        return top==this;
    }
    public boolean consumeDragged(){boolean result=justDragged || dragging && moved;justDragged=false;return result;}
    public boolean isDragging(){return dragging;}
    public void cancel(OrionConfigManager manager){REGIONS.remove(this);if(dragging&&moved)manager.save();if(owner==this)owner=null;dragging=false;resizing=false;moved=false;wasDown=false;justDragged=false;}

    public static void registerOverlay() {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(OrionClient.MOD_ID,"hud_resize_handles"), (context, ticks) -> {
            Minecraft client=Minecraft.getInstance();
            if (!(client.gui.screen() instanceof ChatScreen)) {
                if(handleScale!=0) for(Identifier handle:HANDLES)client.getTextureManager().release(handle);
                handleScale=0;return;
            }
            int scale=Math.max(1,Math.min(6,client.getWindow().getGuiScale()));
            if(handleScale!=scale) {
                for(int active=0;active<2;active++) {
                    client.getTextureManager().release(HANDLES[active]);
                    BufferedImage image=renderHandle(scale,active==1);
                    NativeImage pixels=new NativeImage(image.getWidth(),image.getHeight(),true);
                    OrionHudRenderer.copyPixels(image,pixels);
                    int index=active;
                    client.getTextureManager().register(HANDLES[active],new DynamicTexture(()->"orion_resize_"+index,pixels));
                }
                handleScale=scale;
            }
            double mx=client.mouseHandler.getScaledXPos(client.getWindow()),my=client.mouseHandler.getScaledYPos(client.getWindow());
            for(var entry:REGIONS.entrySet()) {
                Bounds b=entry.getValue();
                if(System.nanoTime()-b.seen>500_000_000L)continue;
                int hx=b.x+b.w-HANDLE_SIZE,hy=b.y+b.h-HANDLE_SIZE;
                boolean active=owner==entry.getKey() && entry.getKey().resizing
                        || mx>=hx&&mx<=hx+HANDLE_SIZE&&my>=hy&&my<=hy+HANDLE_SIZE;
                context.blit(HANDLES[active?1:0],hx,hy,hx+HANDLE_SIZE,hy+HANDLE_SIZE,0,1,0,1);
            }
        });
    }

    static BufferedImage renderHandle(int scale,boolean active) {
        BufferedImage image=new BufferedImage(HANDLE_SIZE*scale,HANDLE_SIZE*scale,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,RenderingHints.VALUE_STROKE_PURE);
            g.scale(scale,scale);g.setColor(new Color(active?0x6DA2FF:0x929297));
            for(int row=0;row<3;row++)for(int col=0;col<=row;col++)
                g.fill(new Ellipse2D.Float(9-col*3.6F,1.5F+row*3.6F,2.6F,2.6F));
        } finally {g.dispose();}
        return image;
    }
}
