package ru.orion.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.orion.client.replay.GleamReplay;

@Mixin(GameRenderer.class)
abstract class GleamReplayCaptureMixin {
    @Shadow @Final private Minecraft minecraft;

    //? if >=26.3 {
    private static final String RENDER = "render()V";
    private static final String GUI = "Lnet/minecraft/client/gui/render/GuiRenderer;render()V";
    //?} else if >=26.2 {
    /*private static final String RENDER = "render(Lnet/minecraft/client/DeltaTracker;Z)V";
    private static final String GUI = "Lnet/minecraft/client/gui/render/GuiRenderer;render()V";
    *///?} else {
    /*private static final String RENDER = "render(Lnet/minecraft/client/DeltaTracker;Z)V";
    private static final String GUI = "Lnet/minecraft/client/gui/render/GuiRenderer;render(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;)V";
    *///?}

    @Inject(method = RENDER, at = @At(value = "INVOKE", target = GUI))
    private void gleam$captureWithoutEditor(CallbackInfo ci) {
        GleamReplay.INSTANCE.captureFrame(minecraft, true);
    }

    @Inject(method = RENDER, at = @At(value = "INVOKE", target = GUI, shift = At.Shift.AFTER))
    private void gleam$captureWithHud(CallbackInfo ci) {
        GleamReplay.INSTANCE.captureFrame(minecraft, false);
    }
}
