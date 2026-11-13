package ru.orion.client.mixin;

//? if >=26.2 {
import net.minecraft.client.renderer.DebugCrosshairRenderer;
//?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.orion.client.OrionClient;
import ru.orion.client.hud.OrionCustomCrosshair;

//? if >=26.2 {
@Mixin(DebugCrosshairRenderer.class)
//?} else {
/*@Mixin(net.minecraft.client.gui.Gui.class)
*///?}
public abstract class OrionDebugCrosshairMixin {
    //? if >=26.3 {
    @Inject(method = "render(Lnet/minecraft/client/renderer/state/level/CameraRenderState;ILcom/mojang/renderpearl/api/textures/GpuTextureView;Lcom/mojang/renderpearl/api/textures/GpuTextureView;)V",
            at = @At("HEAD"), cancellable = true)
    //?} elif >=26.2 {
    /*@Inject(method = "render(Lnet/minecraft/client/renderer/state/level/CameraRenderState;I)V",
            at = @At("HEAD"), cancellable = true)
    *///?} else {
    /*@Inject(method = "extractCrosshair", at = @At("HEAD"), cancellable = true)
    *///?}
    private void orion$hideReplacedDebugCrosshair(CallbackInfo callback) {
        if (OrionCustomCrosshair.replacesVanilla(OrionClient.getConfigOrNull())) callback.cancel();
    }
}
