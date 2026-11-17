package ru.orion.client.effect;

import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.resources.Identifier;
import org.slf4j.LoggerFactory;
import ru.orion.client.GleamMinecraft;
import ru.orion.client.OrionClient;
import ru.orion.client.config.OrionConfig;
import ru.orion.client.gui.OrionClickGuiScreen;

import java.util.concurrent.CompletableFuture;

public final class OrionCleanWater {
    private static volatile int textureOpacity = 100;
    private static CompletableFuture<Void> reload;

    private OrionCleanWater() {}

    public static void register(OrionConfig config) {
        textureOpacity = effectiveOpacity(config);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            int desired = effectiveOpacity(OrionClient.getConfigOrNull());
            if (desired == textureOpacity || GleamMinecraft.screen(client) instanceof OrionClickGuiScreen
                    || GleamMinecraft.overlay(client) != null || reload != null && !reload.isDone()) return;
            textureOpacity = desired;
            reload = client.reloadResourcePacks();
            reload.exceptionally(error -> {
                LoggerFactory.getLogger(OrionClient.MOD_ID).warn("Could not reload Cleanwater textures", error);
                return null;
            });
        });
    }

    private static int effectiveOpacity(OrionConfig config) {
        return config != null && config.isCleanWaterEnabled() ? config.getCleanWaterOpacity() : 100;
    }

    public static boolean clearsFog() {
        var config = OrionClient.getConfigOrNull();
        return config != null && config.isCleanWaterEnabled() && config.isCleanWaterClearFog();
    }

    public static void adjustSprite(Identifier id, NativeImage image) {
        int opacity = textureOpacity;
        if (opacity == 100 || !id.getNamespace().equals("minecraft")) return;
        switch (id.getPath()) {
            case "block/water_still", "block/water_flow", "block/water_overlay" -> {
                for (int y = 0; y < image.getHeight(); y++) {
                    for (int x = 0; x < image.getWidth(); x++) {
                        int pixel = image.getPixel(x, y);
                        int alpha = pixel >>> 24;
                        if (alpha == 0) continue;
                        int adjusted = Math.max(1, (alpha * opacity + 50) / 100);
                        image.setPixel(x, y, (pixel & 0xFFFFFF) | (adjusted << 24));
                    }
                }
            }
        }
    }
}
