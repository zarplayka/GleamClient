package ru.orion.client.mixin;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.orion.client.hud.OrionSaturationOverlay;

@Mixin(Hud.class)
public abstract class OrionSaturationMixin {
    @Unique private final OrionSaturationOverlay orion$saturation = new OrionSaturationOverlay();
    @Shadow public abstract int getGuiTicks();

    @Inject(method = "tick(Z)V", at = @At("HEAD"))
    private void orion$tickFood(boolean paused, CallbackInfo callback) {
        if (!paused && OrionSaturationOverlay.enabled()) orion$saturation.tick();
    }

    @Inject(method = "extractFood", at = @At("HEAD"))
    private void orion$beforeFood(GuiGraphicsExtractor context, Player player, int y, int right, CallbackInfo callback) {
        if (OrionSaturationOverlay.enabled()) orion$saturation.beginFood(context, player, y, right);
    }

    @Redirect(method = "extractFood", at = @At(value = "INVOKE", ordinal = 0,
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V"))
    private void orion$foodPosition(GuiGraphicsExtractor context, RenderPipeline pipeline, Identifier sprite,
                                    int x, int y, int width, int height,
                                    GuiGraphicsExtractor originalContext, Player player, int top, int right) {
        if (OrionSaturationOverlay.enabled()) orion$saturation.positionFoodIcon(right, x, y);
        context.blitSprite(pipeline, sprite, x, y, width, height);
    }

    @Inject(method = "extractFood", at = @At("RETURN"))
    private void orion$drawFood(GuiGraphicsExtractor context, Player player, int y, int right, CallbackInfo callback) {
        if (OrionSaturationOverlay.enabled()) orion$saturation.renderFood(context, player, right);
    }

    @Inject(method = "extractHearts", at = @At("RETURN"))
    private void orion$drawHealing(GuiGraphicsExtractor context, Player player, int left, int top, int lineHeight,
                                   int regenerating, float maxHealth, int lastHealth, int health, int absorption,
                                   boolean blinking, CallbackInfo callback) {
        if (OrionSaturationOverlay.enabled()) {
            orion$saturation.renderHealth(context, player, left, top, lineHeight, regenerating, maxHealth, absorption, getGuiTicks());
        }
    }
}
