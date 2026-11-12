package ru.orion.client.mixin;

import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.orion.client.OrionClient;

/** Мировой post-process вне GuiRenderer: HUD-кеши могут перенаправлять его framebuffer.
 * В 26.2 глубина мира очищается ДО renderItemInHand, поэтому Mirage идёт сразу после мира.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMotionBlurMixin {
    //? if >=26.3 {
    @Inject(method = "renderLevel()V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/GameRenderer;render3dHud(Lnet/minecraft/client/renderer/state/level/CameraRenderState;Lnet/minecraft/client/renderer/state/level/PlayerRenderState;Lnet/minecraft/client/renderer/state/OptionsRenderState;Z)V"))
    //?} else {
        /*@Inject(method = "renderLevel(Lnet/minecraft/client/DeltaTracker;)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;render(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/renderer/state/level/CameraRenderState;Lorg/joml/Matrix4fc;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;Z)V", shift = At.Shift.AFTER))
    *///?}
    private void orion$applyMirageBeforeHands(CallbackInfo callback) {
        var mirage = OrionClient.getMirageOrNull();
        if (mirage != null) mirage.applyToWorld();
    }

    //? if >=26.3 {
    @Inject(method = "render()V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/GameRenderer;lighting()Lcom/mojang/blaze3d/platform/Lighting;"))
    //?} else {
        /*@Inject(method = "render(Lnet/minecraft/client/DeltaTracker;Z)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/GameRenderer;lighting()Lcom/mojang/blaze3d/platform/Lighting;"))
    *///?}
    private void orion$applyMotionBlurBeforeHud(CallbackInfo callback) {
        var motionBlur = OrionClient.getMotionBlurOrNull();
        if (motionBlur != null) motionBlur.applyBeforeGui();
    }
}
