package ru.orion.client.mixin;

import net.minecraft.client.renderer.ScreenEffectRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.orion.client.OrionClient;
import ru.orion.client.config.OrionConfig;
import ru.orion.client.effect.OrionCleanWater;

@Mixin(ScreenEffectRenderer.class)
public class ScreenEffectRendererMixin {
    //? if >=26.2 {
    @Inject(method = "submitWater", at = @At("HEAD"), cancellable = true)
    //?} else {
    /*@Inject(method = "renderWater", at = @At("HEAD"), cancellable = true)
    *///?}
    private static void orion$clearWaterOverlay(CallbackInfo ci) {
        if (OrionCleanWater.clearsFog()) ci.cancel();
    }

    //? if >=26.2 {
    @Inject(method = "submitFire", at = @At("HEAD"), cancellable = true)
    //?} else {
    /*@Inject(method = "renderFire", at = @At("HEAD"), cancellable = true)
    *///?}
    private static void orion$noFireOverlay(CallbackInfo ci) {
        OrionConfig config = OrionClient.getConfigOrNull();
        if (config != null && config.isNoFireEnabled()) {
            ci.cancel();
        }
    }
}
