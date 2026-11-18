package ru.orion.client.effect;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import ru.orion.client.config.OrionConfig;

import java.util.function.BiConsumer;

final class OrionWorldGeometry {
    private OrionWorldGeometry() {
    }

    static boolean isAvailable(WorldRenderContext context) {
        return context.matrixStack() != null && context.consumers() != null;
    }

    static int color(OrionConfig config, int fallback) {
        if (!config.isRainbowEnabled()) {
            return fallback;
        }
        long period = 12_000L / Mth.clamp(config.getRainbowSpeed(), 1, 10);
        float hue = (System.currentTimeMillis() % period) / (float) period;
        return java.awt.Color.HSBtoRGB(hue, 1.0F, 1.0F);
    }

    static int parseColor(String color) {
        return ARGB.opaque(Integer.parseInt(color.substring(1), 16));
    }

    static void draw(WorldRenderContext context, PoseStack poses, RenderType layer,
                     BiConsumer<PoseStack.Pose, VertexConsumer> emitVertices) {
        emitVertices.accept(poses.last(), context.consumers().getBuffer(layer));
    }
}
