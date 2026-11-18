package ru.orion.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import ru.orion.client.GleamMinecraft;
import ru.orion.client.mixin.OrionContainerGeometryAccessor;

import java.util.Random;

public final class GleamScreenSnow {
    private static final int ICE = 0xffc5dfef, WHITE = 0xfff1fbff;
    private static final int PATCH = 18;
    private final Random random = new Random();
    private final float[] x = new float[64], y = new float[x.length], speed = new float[x.length];
    private final int[] depth, snowmen;
    private final OrionContainerGeometryAccessor inventory;
    private final int width, height;
    private long last = System.nanoTime();
    private double age;

    private GleamScreenSnow(int width, int height, OrionContainerGeometryAccessor inventory) {
        this.width = width;
        this.height = height;
        this.inventory = inventory;
        int patches = inventory == null ? 0 : Math.max(1, inventory.orion$getImageWidth() / PATCH);
        depth = new int[patches];
        snowmen = new int[patches];
        for (int i = 0; i < x.length; i++) {
            x[i] = random.nextFloat() * width;
            y[i] = random.nextFloat() * height;
            speed[i] = 14 + random.nextFloat() * 22;
        }
    }

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            boolean inv = screen instanceof InventoryScreen || screen instanceof CreativeModeInventoryScreen;
            if (!inv && !(screen instanceof OrionClickGuiScreen)) return;
            var snow = new GleamScreenSnow(width, height, inv ? (OrionContainerGeometryAccessor) screen : null);
            ScreenEvents.afterExtract(screen).register((current, graphics, mx, my, delta) -> {
                if (GleamMinecraft.screen(client) == current && GleamMinecraft.overlay(client) == null) snow.draw(graphics);
            });
            if (inv) ScreenMouseEvents.allowMouseClick(screen).register((current, event) ->
                    event.button() != InputConstants.MOUSE_BUTTON_LEFT || GleamMinecraft.overlay(client) != null
                            || client.player == null || !client.player.containerMenu.getCarried().isEmpty() || !snow.sculpt(event.x(), event.y()));
        });
    }

    private int patch(double px) {
        double dx = px - inventory.orion$getLeftPos();
        return dx < 0 || dx >= PATCH * depth.length ? -1 : (int) dx / PATCH;
    }

    private boolean sculpt(double px, double py) {
        if (py < 0) return false;
        int i = patch(px);
        if (i < 0 || depth[i] == 0 || snowmen[i] == 3) return false;
        int top = rim();
        int center = inventory.orion$getLeftPos() + i * PATCH + PATCH / 2;
        boolean mound = py < top && py >= top - depth[i];
        boolean figure = snowmen[i] > 0 && Math.abs(px-center) <= 5 && py >= top - snowmen[i]*5 - 2 && py < top;
        if (!mound && !figure) return false;
        snowmen[i]++;
        depth[i]--;
        return true;
    }

    private int rim() {
        return inventory.orion$getTopPos() - (inventory instanceof CreativeModeInventoryScreen ? 29 : 0);
    }

    private void draw(GuiGraphicsExtractor g) {
        g.nextStratum();
        long now = System.nanoTime();
        float dt = Math.min(.1F, (now-last)/1_000_000_000F);
        last = now;
        age += dt;
        int top = inventory == null ? 0 : rim();
        for (int i=0; i<x.length; i++) {
            float old = y[i];
            x[i] += (float)Math.sin(age*.8 + i) * 7 * dt;
            if (x[i]<0) x[i]+=width;
            if (x[i]>=width) x[i]-=width;
            y[i] += speed[i]*dt;
            if (inventory != null) {
                int slot = patch(x[i]);
                if (slot >= 0 && old < top-depth[slot] && y[i] >= top-depth[slot]) {
                    depth[slot] = Math.min(6,depth[slot]+1);
                    respawn(i);
                    continue;
                }
                // Snow falls behind the panel, keeping item stacks and text legible.
                if (x[i]>=inventory.orion$getLeftPos() && x[i]<inventory.orion$getLeftPos()+inventory.orion$getImageWidth()
                        && y[i]>=top && y[i]<inventory.orion$getTopPos()+inventory.orion$getImageHeight()) continue;
            }
            if (y[i]>=height) { respawn(i); continue; }
            int px=(int)x[i], py=(int)y[i], radius=speed[i]>30 ? 2 : 1;
            g.fill(px,py,px+radius,py+radius,radius==2 ? 0xbde3f4ff : 0x99ecf9ff);
        }
        if (inventory == null) return;
        for (int i=0; i<depth.length; i++) {
            int left=inventory.orion$getLeftPos()+i*PATCH;
            if (depth[i]>0) {
                g.fill(left,top-depth[i]+1,left+PATCH,top,ICE);
                g.fill(left+1,top-depth[i],left+PATCH-1,top-depth[i]+1,WHITE);
            }
            if (snowmen[i]>0) figure(g,left+PATCH/2,top-1,snowmen[i]);
        }
    }

    private void respawn(int i) {
        x[i]=random.nextFloat()*width;
        y[i]=-random.nextFloat()*8;
    }

    private static void ball(GuiGraphicsExtractor g,int x,int bottom,int r) {
        g.fill(x-r+1,bottom-r*2,x+r,bottom,WHITE);
        g.fill(x-r,bottom-r*2+1,x+r+1,bottom-1,ICE);
        g.fill(x-r+1,bottom-r*2+1,x+r-1,bottom-2,WHITE);
    }

    private static void figure(GuiGraphicsExtractor g,int x,int bottom,int stage) {
        ball(g,x,bottom,4);
        if (stage < 2) return;
        ball(g,x,bottom-6,3);
        g.fill(x,bottom-9,x+1,bottom-8,0xff48505a);
        if (stage < 3) return;
        ball(g,x,bottom-10,2);
        g.fill(x-2,bottom-14,x-1,bottom-13,0xff333840);
        g.fill(x,bottom-14,x+1,bottom-13,0xff333840);
        g.fill(x,bottom-13,x+3,bottom-12,0xffec873a);
        g.fill(x-3,bottom-11,x+3,bottom-10,0xffba4c44);
    }
}