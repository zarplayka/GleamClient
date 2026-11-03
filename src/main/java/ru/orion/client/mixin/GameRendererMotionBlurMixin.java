package ru.orion.client.mixin;

import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.orion.client.OrionClient;

/** Мировой post-process вне GuiRenderer: HUD-кеши могут перенаправлять его framebuffer. */
@Mixin(GameRenderer.class)
public abstract class GameRendererMotionBlurMixin {
    @Inject(method = "render()V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/GameRenderer;lighting()Lcom/mojang/blaze3d/platform/Lighting;"))
    private void orion$applyMotionBlurBeforeHud(CallbackInfo callback) {
        var motionBlur = OrionClient.getMotionBlurOrNull();
        if (motionBlur != null) motionBlur.applyBeforeGui();
    }
}
