package ru.orion.client.hud;

import ru.orion.client.GleamMinecraft;

import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.platform.InputConstants;
import ru.orion.client.mixin.OrionMouseHandlerAccessor;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.util.Mth;
import ru.orion.client.config.OrionConfigManager;

import java.util.function.BiConsumer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.resources.Identifier;
import ru.orion.client.OrionClient;

/** Захват одного HUD-элемента при перетаскивании в открытом чате. */
public final class OrionHudDrag {
    private final String id;
    public OrionHudDrag(String id) { this.id = id; }
    private static final int HANDLE_SIZE = 13;
    private static final OrionHudView HANDLE = new OrionHudView("hud_resize_handles");
    private static OrionHudDrag owner;
    private static final Map<OrionHudDrag, Bounds> REGIONS = new LinkedHashMap<>();
    private static final Map<String, Bounds> FIXED_REGIONS = new LinkedHashMap<>();
    private record Bounds(int x, int y, int w, int h, long seen) {
        boolean contains(double mx, double my) {
            return System.nanoTime() - seen < 500_000_000L && mx >= x && mx <= x+w && my >= y && my <= y+h;
        }
        boolean overlaps(int left, int top, int width, int height, int gap) {
            return left < x + w + gap && x - gap < left + width
                    && top < y + h + gap && y - gap < top + height;
        }
    }
    private boolean wasDown, dragging, moved, justDragged, resizing;
    private double offsetX, offsetY, pressX, pressY;
    private int initialWidth, initialHeight, anchorX, anchorY;
    private float initialScale;
    public record Position(int x,int y) { }

    public static void observe(String id, int x, int y, int width, int height) {
        if (width > 0 && height > 0) FIXED_REGIONS.put(id, new Bounds(x, y, width, height, System.nanoTime()));
    }

    public static void forget(String id) { FIXED_REGIONS.remove(id); }

    Position available(GuiGraphicsExtractor context, int x, int y, int width, int height) {
        long now = System.nanoTime();
        var occupied = new ArrayList<Bounds>();
        for (var entry : REGIONS.entrySet()) {
            if (entry.getKey() != this && now - entry.getValue().seen < 500_000_000L) occupied.add(entry.getValue());
        }
        for (var bounds : FIXED_REGIONS.values()) {
            if (now - bounds.seen < 500_000_000L) occupied.add(bounds);
        }
        int gap = 4;
        if (occupied.stream().noneMatch(b -> b.overlaps(x, y, width, height, gap))) return new Position(x, y);
        int maxX = context.guiWidth() - width, maxY = context.guiHeight() - height;
        var xs = new ArrayList<>(java.util.List.of(x, 0, maxX));
        var ys = new ArrayList<>(java.util.List.of(y, 0, maxY));
        for (var bounds : occupied) {
            xs.add(Math.clamp(bounds.x - width - gap, 0, maxX));
            xs.add(Math.clamp(bounds.x + bounds.w + gap, 0, maxX));
            ys.add(Math.clamp(bounds.y - height - gap, 0, maxY));
            ys.add(Math.clamp(bounds.y + bounds.h + gap, 0, maxY));
        }
        Position nearest = null;
        int shortest = Integer.MAX_VALUE;
        for (int left : xs) {
            for (int top : ys) {
                int distance = Math.abs(left - x) + Math.abs(top - y);
                if (distance >= shortest) continue;
                if (occupied.stream().noneMatch(b -> b.overlaps(left, top, width, height, gap))) {
                    nearest = new Position(left, top);
                    shortest = distance;
                }
            }
        }
        return nearest;
    }

    void trackPosition(Position position, int width, int height) {
        REGIONS.put(this, new Bounds(position.x, position.y, width, height, System.nanoTime()));
    }

    Position placeWidget(GuiGraphicsExtractor context, int width, int height, int defaultX, int defaultY,
                         OrionConfigManager manager) {
        var config = manager.getConfig();
        int savedX = config.getExtraHudPosition(id, 0);
        int savedY = config.getExtraHudPosition(id, 1);
        int x = Math.clamp(savedX < 0 ? defaultX : savedX, 0, context.guiWidth() - width);
        int y = Math.clamp(savedY < 0 ? defaultY : savedY, 0, context.guiHeight() - height);
        Position available = available(context, x, y, width, height);
        if (available == null) {
            cancel(manager);
            return null;
        }
        Position requested = update(context, available.x, available.y, width, height, manager, (nx, ny) -> {
            Position safe = available(context, nx, ny, width, height);
            if (safe != null) config.setExtraHudPosition(id, safe.x, safe.y);
        });
        Position result = available(context, requested.x, requested.y, width, height);
        if (result == null) result = available;
        trackPosition(result, width, height);
        return result;
    }
    public Position update(GuiGraphicsExtractor context,int x,int y,int w,int h,OrionConfigManager manager,BiConsumer<Integer,Integer> setPosition) {
        return update(context, x, y, w, h, manager, setPosition, true);
    }
    public Position update(GuiGraphicsExtractor context,int x,int y,int w,int h,OrionConfigManager manager,BiConsumer<Integer,Integer> setPosition,boolean canStart) {
        Minecraft client=Minecraft.getInstance();
        boolean open=GleamMinecraft.screen(client) instanceof ChatScreen;
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
            if (!(GleamMinecraft.screen(client) instanceof ChatScreen)) {
                HANDLE.dispose();
                return;
            }
            if (REGIONS.isEmpty()) return;
            var scene = new OrionHudView.Scene(context.guiWidth(), context.guiHeight());
            double mx=client.mouseHandler.getScaledXPos(client.getWindow()),my=client.mouseHandler.getScaledYPos(client.getWindow());
            for(var entry:REGIONS.entrySet()) {
                Bounds b=entry.getValue();
                if(System.nanoTime()-b.seen>500_000_000L)continue;
                int hx=b.x+b.w-HANDLE_SIZE,hy=b.y+b.h-HANDLE_SIZE;
                boolean active=owner==entry.getKey() && entry.getKey().resizing
                        || mx>=hx&&mx<=hx+HANDLE_SIZE&&my>=hy&&my<=hy+HANDLE_SIZE;
                for (int row = 0; row < 3; row++) for (int col = 0; col <= row; col++) {
                    scene.box(hx + 9 - col * 3.6F, hy + 1.5F + row * 3.6F, 2.6F, 2.6F, 1.3F,
                            active ? "#6da2ff" : "#929297", null);
                }
            }
            HANDLE.draw(context, scene, 0, 0, context.guiWidth(), context.guiHeight());
        });
    }
}
