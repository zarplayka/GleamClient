package ru.orion.client.hud;

import net.minecraft.client.gui.GuiGraphicsExtractor;
/** Передаёт границы ванильного скорборда для привязки соседних виджетов. */
public final class OrionScoreboardGlass {
    private boolean drawn;
    private int headerX0;
    private int headerY0;
    private int headerX1;
    private int headerY1;

    public void beginFrame() {
        drawn = false;
    }

    public void endFrame() {
        if (!drawn) {
            OrionHudDrag.forget("scoreboard");
        }
    }

    public void header(GuiGraphicsExtractor context, int x0, int y0, int x1, int y1, int color) {
        headerX0 = x0;
        headerY0 = y0;
        headerX1 = x1;
        headerY1 = y1;
        context.fill(x0, y0, x1, y1, color);
    }

    public void body(GuiGraphicsExtractor context, int x0, int y0, int x1, int y1, int color) {
        int left = Math.min(headerX0, x0);
        int top = Math.min(headerY0, y0);
        OrionHudDrag.observe("scoreboard", left, top, Math.max(headerX1, x1) - left, Math.max(headerY1, y1) - top);
        context.fill(x0, y0, x1, y1, color);
        drawn = true;
    }

}
