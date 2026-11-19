package ru.orion.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import ru.orion.client.GleamMinecraft;
import ru.orion.client.effect.OrionScreenMotion;
import ru.orion.client.mixin.OrionContainerGeometryAccessor;

import java.util.Random;

public final class GleamScreenSnow {
    private static final int PATCH = 27;
    private static final int PALE = 0xffeef8ff;
    private static final int BLUE = 0xffcedfed;
    private static final int SHADE = 0xff91aec8;

    private final Random random = new Random();
    private final Flake[] flakes;
    private final Drift[] drifts;
    private final int width;
    private final int height;
    private final Screen container;
    private final OrionContainerGeometryAccessor geometry;
    private long lastFrame;
    private double time;

    GleamScreenSnow(int width, int height, Screen container) {
        this.width = width;
        this.height = height;
        this.container = container;
        geometry = container == null ? null : (OrionContainerGeometryAccessor) container;
        int count = (int) Math.clamp(width * (double) height / 1850, 36, 150);
        flakes = new Flake[count];
        for (int i = 0; i < count; i++) {
            flakes[i] = new Flake(i / (float) count);
            reset(flakes[i], random.nextFloat() * height);
        }
        drifts = new Drift[geometry == null ? 0 : geometry.orion$getImageWidth() / PATCH];
        for (int i = 0; i < drifts.length; i++) drifts[i] = new Drift(random.nextFloat() * 6.28F, 2F + random.nextFloat());
    }

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!(screen instanceof AbstractContainerScreen<?>)) return;
            var snow = new GleamScreenSnow(width, height, screen);
            ScreenEvents.afterExtract(screen).register((current, graphics, mx, my, delta) -> {
                if (GleamMinecraft.screen(client) == current && GleamMinecraft.overlay(client) == null) {
                    snow.draw(graphics);
                }
            });
            ScreenMouseEvents.allowMouseClick(screen).register((current, event) ->
                    event.button() != InputConstants.MOUSE_BUTTON_LEFT || GleamMinecraft.overlay(client) != null
                            || client.player == null || !client.player.containerMenu.getCarried().isEmpty()
                            || !snow.sculpt(event.x(), event.y()));
        });
    }

    boolean fits(int width, int height) {
        return this.width == width && this.height == height;
    }

    void draw(GuiGraphicsExtractor g) {
        long now = System.nanoTime();
        float dt = lastFrame == 0 ? 0F : Math.min(.05F, (now - lastFrame) / 1_000_000_000F);
        lastFrame = now;
        time += dt;
        Rim rim = geometry == null ? null : rim();
        g.nextStratum();

        for (Flake flake : flakes) {
            float previousY = flake.y;
            double wind = 3.8 + Math.sin(time * .43 + flake.y / 110F) * 5.4;
            flake.x += dt * (float) (wind * (0.5 + flake.depth) + Math.sin(time * flake.swing + flake.phase) * (3 + flake.depth * 9));
            flake.y += flake.speed * dt;
            if (flake.x < -5) flake.x += width + 10;
            if (flake.x > width + 5) flake.x -= width + 10;
            if (flake.y > height + 4) reset(flake, -4);
            if (rim != null && flake.x >= rim.left && flake.x < rim.right) {
                float local = (flake.x - rim.left) / rim.scale;
                int index = (int) local / PATCH;
                if (index < drifts.length) {
                    Drift drift = drifts[index];
                    float surface = rim.top - rim.scale * drift.height(local % PATCH);
                    if (previousY < surface && flake.y >= surface) {
                        drift.snow = Math.min(9F, drift.snow + .54F + flake.depth * .58F);
                        reset(flake, -4);
                        continue;
                    }
                }
                if (flake.y >= rim.top - 1 && flake.y < rim.bottom + 1) continue;
            }
            drawFlake(g, flake);
        }
        if (rim == null) return;
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(rim.left, rim.top);
        pose.scale(rim.scale, rim.scale);
        for (int i = 0; i < drifts.length; i++) {
            Drift drift = drifts[i];
            for (int px = 0; px < PATCH; px++) {
                int depth = Math.round(drift.height(px + .5F));
                if (depth == 0) continue;
                int x = i * PATCH + px;
                g.fill(x, -depth, x + 1, 0, depth > 2 ? BLUE : PALE);
                if (depth > 2) {
                    g.fill(x, -depth, x + 1, -depth + 1, PALE);
                    g.fill(x, -1, x + 1, 0, SHADE);
                }
            }
            if (drift.stage > 0) drawSnowman(g, i * PATCH + PATCH / 2, drift.stage);
        }
        pose.popMatrix();
    }

    private Rim rim() {
        var motion = (OrionScreenMotion) container;
        float scale = motion.orion$frameScale();
        float cx = motion.orion$inventoryCenterX();
        float cy = motion.orion$inventoryCenterY();
        float left = cx + (geometry.orion$getLeftPos() - cx) * scale;
        int tabHeight = container instanceof CreativeModeInventoryScreen ? 29 : 0;
        float top = cy + (geometry.orion$getTopPos() - tabHeight - cy) * scale;
        return new Rim(left, top, left + geometry.orion$getImageWidth() * scale,
                cy + (geometry.orion$getTopPos() + geometry.orion$getImageHeight() - cy) * scale, scale);
    }

    private boolean sculpt(double x, double y) {
        Rim rim = rim();
        double localX = (x - rim.left) / rim.scale;
        double localY = (rim.top - y) / rim.scale;
        if (localX < 0 || localY <= 0 || y < 0) return false;
        int i = (int) localX / PATCH;
        if (i >= drifts.length) return false;
        Drift drift = drifts[i];
        if (drift.stage == 3 || drift.snow < 1.6F) return false;
        float patchX = (float) (localX - i * PATCH);
        boolean mound = localY <= Math.round(drift.height(patchX));
        boolean figure = drift.stage > 0 && Math.abs(patchX - PATCH / 2) <= 8 && localY <= (drift.stage == 1 ? 10 : 16);
        if (!mound && !figure) return false;
        drift.stage++;
        drift.snow -= 1.6F;
        return true;
    }

    private void reset(Flake flake, float y) {
        flake.x = random.nextFloat() * width;
        flake.y = y;
        flake.speed = 11 + flake.depth * 24 + random.nextFloat() * 8;
        flake.phase = random.nextFloat() * 6.28F;
        flake.swing = .9F + random.nextFloat() * 1.8F;
    }

    private void drawFlake(GuiGraphicsExtractor g, Flake flake) {
        int x = Math.round(flake.x), y = Math.round(flake.y);
        float twinkle = .87F + .13F * (float) Math.sin(time * 2.2 + flake.phase);
        int opaque = (int) ((67 + flake.depth * 145) * twinkle);
        if (flake.depth > .86F) {
            int edge = opaque / 3 << 24 | 0xbad9f6;
            g.fill(x - 2, y, x + 3, y + 1, edge);
            g.fill(x, y - 2, x + 1, y + 3, edge);
            g.fill(x - 1, y, x + 2, y + 1, (opaque * 2 / 3 << 24) | 0xdef1ff);
            g.fill(x, y - 1, x + 1, y + 2, (opaque * 2 / 3 << 24) | 0xdef1ff);
        } else if (flake.depth > .54F) {
            g.fill(x, y, x + 2, y + 2, (opaque / 2 << 24) | 0xd3eaff);
        }
        g.fill(x, y, x + 1, y + 1, (opaque << 24) | 0xf0f8ff);
    }

    private static void drawSnowman(GuiGraphicsExtractor g, int x, int stage) {
        ball(g, x, 0, 8, 10);
        if (stage < 2) return;
        g.fill(x - 10, -13, x - 7, -12, 0xff785742);
        g.fill(x - 7, -12, x - 4, -11, 0xff785742);
        g.fill(x + 5, -12, x + 8, -11, 0xff785742);
        g.fill(x + 8, -14, x + 9, -11, 0xff785742);
        ball(g, x, -7, 6, 9);
        g.fill(x + 1, -7, x + 2, -6, 0xff445870);
        g.fill(x + 1, -4, x + 2, -3, 0xff445870);
        if (stage < 3) return;
        ball(g, x - 1, -13, 5, 8);
        g.fill(x - 4, -19, x - 3, -18, 0xff293d50);
        g.fill(x, -19, x + 1, -18, 0xff293d50);
        g.fill(x - 1, -18, x + 3, -17, 0xffdc783c);
        g.fill(x - 6, -15, x + 5, -13, 0xffc95454);
        g.fill(x - 5, -15, x + 3, -14, 0xfff2796a);
        g.fill(x + 3, -14, x + 5, -9, 0xffad3d49);
        g.fill(x - 6, -22, x + 5, -21, 0xff25384d);
        g.fill(x - 4, -26, x + 3, -22, 0xff324961);
        g.fill(x - 4, -23, x + 3, -22, 0xffb84b56);
    }

    private static void ball(GuiGraphicsExtractor g, int x, int bottom, int radius, int height) {
        for (int row = 0; row < height; row++) {
            double curve = (row + .5) / height * 2 - 1;
            int half = (int) Math.round(Math.sqrt(1 - curve * curve) * radius);
            int y = bottom - height + row;
            int shadow = row == height - 1 ? SHADE : BLUE;
            g.fill(x - half, y, x + half + 1, y + 1, shadow);
            if (row < height - 2) g.fill(x - half + 1, y, x + half - 1, y + 1, PALE);
        }
    }

    private static final class Flake {
        private final float depth;
        private float x, y, speed, phase, swing;

        private Flake(float depth) { this.depth = depth; }
    }

    private static final class Drift {
        private final float phase;
        private float snow;
        private int stage;

        private Drift(float phase, float snow) {
            this.phase = phase;
            this.snow = snow;
        }

        private float height(float x) {
            double center = x / PATCH * 2 - 1;
            double taper = Math.max(0, 1 - center * center);
            return snow * (float) (taper * (.74 + .26 * Math.sin(x * .39 + phase)));
        }
    }

    private record Rim(float left, float top, float right, float bottom, float scale) {}
}
