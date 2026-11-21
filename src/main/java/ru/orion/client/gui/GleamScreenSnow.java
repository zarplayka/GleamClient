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
        int count = (int) Math.clamp(width * (double) height / (inventory ? 3900 : 2400),
                inventory ? 14 : 24, inventory ? 32 : 68);
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
        if (lastFrame == 0) lastFrame = now;
        boolean step = now - lastFrame >= 33_000_000L;
        float dt = step ? Math.min(.06F, (now - lastFrame) / 1_000_000_000F) : 0F;
        if (step) lastFrame = now;
        time += dt;
        graphics.nextStratum();
        for (Flake flake : flakes) {
            if (step) {
                double oscillation = Math.sin(time * flake.swing + flake.phase);
                flake.x += dt * (float) (4 + oscillation * (3 + flake.depth * 11));
                flake.y += dt * flake.speed;
                if (flake.x < -8) flake.x += width + 16;
                if (flake.x > width + 8) flake.x -= width + 16;
                if (flake.y > height + 8) reset(flake, -random.nextFloat(8F, 22F));
            }
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
        int light = Math.round((70 + flake.depth * 150) * (inventory ? .70F : .94F));
        if (flake.depth > .72F) {
            int arm = flake.depth > .94F ? 2 : 1;
            int color = (light / 3) << 24 | 0xb6d7ee;
            graphics.fill(x - arm, y, x + arm + 1, y + 1, color);
            graphics.fill(x, y - arm, x + 1, y + arm + 1, color);
        }
        dot(graphics, x, y, 0xf2f9ff, light);
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
