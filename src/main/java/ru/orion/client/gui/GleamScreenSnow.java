package ru.orion.client.gui;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import ru.orion.client.GleamMinecraft;

import java.util.Random;

public final class GleamScreenSnow {
    private final Random random = new Random();
    private final Flake[] flakes;
    private final int width;
    private final int height;
    private final boolean inventory;
    private long lastFrame;
    private double time;

    private GleamScreenSnow(int width, int height, boolean inventory) {
        this.width = width;
        this.height = height;
        this.inventory = inventory;
        int count = (int) Math.clamp(width * (double) height / (inventory ? 2700 : 1470), inventory ? 25 : 42, 160);
        flakes = new Flake[count];
        for (int i = 0; i < count; i++) {
            flakes[i] = new Flake((i + random.nextFloat()) / count);
            reset(flakes[i], random.nextFloat(-8F, Math.max(1, height)));
        }
    }

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            boolean inventory = screen instanceof AbstractContainerScreen<?>;
            if (!inventory && !(screen instanceof OrionClickGuiScreen)) return;
            var snow = new GleamScreenSnow(width, height, inventory);
            ScreenEvents.afterExtract(screen).register((current, graphics, mx, my, delta) -> {
                if (GleamMinecraft.screen(client) == current && GleamMinecraft.overlay(client) == null) {
                    snow.draw(graphics);
                }
            });
        });
    }

    private void draw(GuiGraphicsExtractor graphics) {
        long now = System.nanoTime();
        float dt = lastFrame == 0 ? 0F : Math.min(.05F, (now - lastFrame) / 1_000_000_000F);
        lastFrame = now;
        time += dt;
        graphics.nextStratum();
        for (Flake flake : flakes) {
            double depth = flake.depth;
            double gust = 5 + 7 * Math.sin(time * .46 + flake.y * .025);
            flake.x += dt * (float) (gust * (.45 + depth) + Math.sin(time * flake.swing + flake.phase) * (3 + depth * 11));
            flake.y += dt * flake.speed * (1F + .10F * (float) Math.cos(time * flake.swing + flake.phase));
            if (flake.x < -8) flake.x += width + 16;
            if (flake.x > width + 8) flake.x -= width + 16;
            if (flake.y > height + 8) reset(flake, -random.nextFloat(8F, 22F));
            drawFlake(graphics, flake);
        }
    }

    private void reset(Flake flake, float y) {
        flake.x = random.nextFloat() * width;
        flake.y = y;
        flake.speed = 9 + flake.depth * 28 + random.nextFloat() * 6;
        flake.phase = random.nextFloat() * 6.28F;
        flake.swing = .72F + random.nextFloat() * 1.4F;
    }

    private void drawFlake(GuiGraphicsExtractor graphics, Flake flake) {
        int x = Math.round(flake.x), y = Math.round(flake.y);
        float twinkle = .89F + .11F * (float) Math.sin(time * 1.8 + flake.phase);
        int light = Math.round((52 + flake.depth * 151) * twinkle * (inventory ? .54F : .94F));
        if (flake.depth > .92F) {
            drawCrystal(graphics, x, y, flake.phase + time * .52, light);
        } else if (flake.depth > .59F) {
            dot(graphics, x - 1, y, 0xb6d7ee, light / 3);
            dot(graphics, x + 1, y, 0xb6d7ee, light / 3);
            dot(graphics, x, y + 1, 0xb6d7ee, light / 3);
        }
        dot(graphics, x, y, 0xf2f9ff, light);
    }

    private static void drawCrystal(GuiGraphicsExtractor graphics, int x, int y, double angle, int light) {
        for (int branch = 0; branch < 6; branch++) {
            double direction = angle + branch * Math.PI / 3;
            for (int step = 1; step <= 3; step++) {
                int dx = (int) Math.round(Math.cos(direction) * step);
                int dy = (int) Math.round(Math.sin(direction) * step);
                dot(graphics, x + dx, y + dy, 0xcde7fc, light * (5 - step) / 5);
            }
            for (int side = -1; side <= 1; side += 2) {
                double tip = direction + side * .39;
                dot(graphics, x + (int) Math.round(3.6 * Math.cos(tip)),
                        y + (int) Math.round(3.6 * Math.sin(tip)), 0xaacbe5, light / 3);
            }
        }
    }

    private static void dot(GuiGraphicsExtractor graphics, int x, int y, int rgb, int alpha) {
        graphics.fill(x, y, x + 1, y + 1, alpha << 24 | rgb);
    }

    private static final class Flake {
        private final float depth;
        private float x, y, speed, phase, swing;

        private Flake(float depth) { this.depth = depth; }
    }
}
