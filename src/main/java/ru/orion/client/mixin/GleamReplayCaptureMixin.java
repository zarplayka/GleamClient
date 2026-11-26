package ru.orion.client.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.orion.client.replay.GleamReplay;

@Mixin(Minecraft.class)
abstract class GleamReplayCaptureMixin {
    //? if >=26.3 {
    @Inject(method = "renderFrame(Z)V", at = @At(value = "INVOKE", target = "Lcom/mojang/renderpearl/api/device/GpuSurface;present()V"))
    //?} else if >=26.2 {
    /*@Inject(method = "renderFrame(Z)V", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/GpuSurface;present()V"))
    *///?} else if >=26.1.2 {
    /*@Inject(method = "renderFrame(Z)V", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;flipFrame(Lcom/mojang/blaze3d/TracyFrameCapture;)V"))
    *///?} else {
    /*@Inject(method = "runTick(Z)V", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/Window;updateDisplay(Lcom/mojang/blaze3d/TracyFrameCapture;)V"))
    *///?}
    private void gleam$captureFrame(boolean tick, CallbackInfo ci) {
        GleamReplay.INSTANCE.captureFrame((Minecraft) (Object) this);
    }
}
