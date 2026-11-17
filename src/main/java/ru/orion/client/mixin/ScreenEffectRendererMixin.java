package ru.orion.client.mixin;

import net.minecraft.client.renderer.ScreenEffectRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.orion.client.OrionClient;
import ru.orion.client.config.OrionConfig;
import ru.orion.client.effect.OrionCleanWater;
import ru.orion.client.effect.OrionNoRender;

@Mixin(ScreenEffectRenderer.class)
public class ScreenEffectRendererMixin {
    //? if >=26.2 {
    @Inject(method = "submitWater", at = @At("HEAD"), cancellable = true)
    //?} else {
    /*@Inject(method = "renderWater", at = @At("HEAD"), cancellable = true)
    *///?}
    private static void orion$clearWaterOverlay(CallbackInfo ci) {
        if (OrionCleanWater.clearsFog() || OrionNoRender.hides(OrionNoRender.Option.WATER_OVERLAY)) ci.cancel();
    }

    //? if >=26.2 {
    @Inject(method = "submitFire", at = @At("HEAD"), cancellable = true)
    //?} else {
    /*@Inject(method = "renderFire", at = @At("HEAD"), cancellable = true)
    *///?}
    private static void orion$noFireOverlay(CallbackInfo ci) {
        OrionConfig config = OrionClient.getConfigOrNull();
        if (config != null && config.isNoFireEnabled() || OrionNoRender.hides(config, OrionNoRender.Option.FIRE_OVERLAY)) {
            ci.cancel();
        }
    }

    //? if >=26.2 {
    @Inject(method = "submitBlockSprite", at = @At("HEAD"), cancellable = true)
    //?} else {
    /*@Inject(method = "renderTex", at = @At("HEAD"), cancellable = true)
    *///?}
    private static void orion$insideBlock(CallbackInfo callback) {
        if (OrionNoRender.hides(OrionNoRender.Option.BLOCK_OVERLAY)) callback.cancel();
    }

    @Inject(method = "renderItemActivationAnimation", at = @At("HEAD"), cancellable = true)
    private void orion$totem(CallbackInfo callback) {
        if (OrionNoRender.hides(OrionNoRender.Option.TOTEM)) callback.cancel();
    }
}
