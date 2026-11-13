package ru.orion.client.hud;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import ru.orion.client.OrionClient;
import ru.orion.client.config.OrionConfig;


/** Меняет только фон sidebar: содержимое и шрифты остаются под управлением Minecraft. */
public final class OrionScoreboardGlass {
    private static final int PAD_X = 6;
    private static final int PAD_Y = 4;
    private static final float PANEL_ARC = 10.0F;

    private final OrionHudView view = new OrionHudView("scoreboard_glass");
    private OrionHudView.Scene scene;
    private int cachedWidth;
    private int cachedHeight;
    private boolean active;
    private boolean drawn;
    private int headerX0;
    private int headerY0;
    private int headerX1;
    private int headerY1;
    private int headerColor;

    public void beginFrame() {
        drawn = false;
    }

    public void endFrame() {
        if (!drawn) {
            release();
        }
    }

    public void beginSidebar(GuiGraphicsExtractor context) {
        OrionConfig config = OrionClient.getConfigOrNull();
        active = config != null && config.isScoreboardGlassEnabled();
        if (active) {
            // Отступ справа позволяет скруглить кромку, не накладывая её на числа.
            context.pose().pushMatrix();
            context.pose().translate(-PAD_X, 0);
        }
    }

    public void endSidebar(GuiGraphicsExtractor context) {
        if (active) {
            context.pose().popMatrix();
        }
        active = false;
    }

    public void header(GuiGraphicsExtractor context, int x0, int y0, int x1, int y1, int color) {
        if (!active) {
            context.fill(x0, y0, x1, y1, color);
            return;
        }
        headerX0 = x0;
        headerY0 = y0;
        headerX1 = x1;
        headerY1 = y1;
        headerColor = color;
    }

    public void body(GuiGraphicsExtractor context, int x0, int y0, int x1, int y1, int color) {
        if (!active) {
            context.fill(x0, y0, x1, y1, color);
            return;
        }
        int left = Math.max(PAD_X, Math.min(headerX0, x0) - PAD_X);
        int top = Math.max(0, Math.min(headerY0, y0) - PAD_Y);
        int right = Math.min(context.guiWidth() + PAD_X - 1, Math.max(headerX1, x1) + PAD_X);
        int bottom = Math.min(context.guiHeight(), Math.max(headerY1, y1) + PAD_Y);
        int width = right - left;
        int height = bottom - top;
        if (width <= 0 || height <= 0) {
            context.fill(headerX0, headerY0, headerX1, headerY1, headerColor);
            context.fill(x0, y0, x1, y1, color);
            return;
        }
        if (scene == null || width != cachedWidth || height != cachedHeight) {
            scene = new OrionHudView.Scene(width, height);
            scene.panel(0, 0, width, height, PANEL_ARC / 2);
            cachedWidth = width;
            cachedHeight = height;
        }
        if (!view.draw(context, scene, left, top, width, height)) {
            context.fill(headerX0, headerY0, headerX1, headerY1, headerColor);
            context.fill(x0, y0, x1, y1, color);
        }
        drawn = true;
    }

    private void release() {
        view.dispose();
    }
}
