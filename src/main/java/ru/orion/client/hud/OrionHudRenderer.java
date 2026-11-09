package ru.orion.client.hud;

import com.mojang.blaze3d.platform.NativeImage;
import java.awt.Color;
import java.awt.image.BufferedImage;

/** Общие операции с пикселями HUD. Остров HUD и музыки рисует OrionMediaPlayerHud. */
public final class OrionHudRenderer {
    private OrionHudRenderer() { }

    public static void copyPixels(BufferedImage source, NativeImage target) {
        int width = source.getWidth(), height = source.getHeight();
        int[] argb = source.getRGB(0, 0, width, height, null, 0, width);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) target.setPixel(x, y, argb[y * width + x]);
        }
    }

    static Color parseColor(String value) {
        try {
            return new Color(Integer.parseUnsignedInt(value.substring(1), 16));
        } catch (RuntimeException invalid) {
            return new Color(0xFF7C00);
        }
    }
}
