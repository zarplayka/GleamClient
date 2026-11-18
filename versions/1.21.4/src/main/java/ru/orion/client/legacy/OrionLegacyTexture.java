package ru.orion.client.legacy;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.awt.image.BufferedImage;

final class OrionLegacyTexture {
    private final ResourceLocation id;
    private DynamicTexture texture;
    private int width;
    private int height;

    OrionLegacyTexture(String path) {
        id = ResourceLocation.fromNamespaceAndPath(OrionLegacyClient.MOD_ID, path);
    }

    void update(BufferedImage image) {
        if (texture == null || width != image.getWidth() || height != image.getHeight()) {
            release();
            width = image.getWidth();
            height = image.getHeight();
            NativeImage pixels = new NativeImage(width, height, true);
            copyPixels(image, pixels);
            texture = new DynamicTexture(pixels);
            Minecraft.getInstance().getTextureManager().register(id, texture);
        } else {
            copyPixels(image, texture.getPixels());
            texture.upload();
        }
    }

    void draw(GuiGraphics graphics, int x, int y, int guiWidth, int guiHeight) {
        graphics.blit(RenderType::guiTextured, id, x, y, 0.0F, 0.0F,
                guiWidth, guiHeight, width, height, width, height);
    }

    void release() {
        if (texture != null) {
            Minecraft.getInstance().getTextureManager().release(id);
            texture = null;
        }
    }

    private static void copyPixels(BufferedImage image, NativeImage pixels) {
        int width = image.getWidth();
        int height = image.getHeight();
        int[] colors = image.getRGB(0, 0, width, height, null, 0, width);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                pixels.setPixel(x, y, colors[y * width + x]);
            }
        }
    }
}
