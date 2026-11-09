package ru.orion.client.mixin;

import net.minecraft.client.renderer.DebugCrosshairRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.orion.client.OrionClient;
import ru.orion.client.hud.OrionCustomCrosshair;

@Mixin(DebugCrosshairRenderer.class)
public abstract class OrionDebugCrosshairMixin {
    @Inject(method = "render(Lnet/minecraft/client/renderer/state/level/CameraRenderState;ILcom/mojang/renderpearl/api/textures/GpuTextureView;Lcom/mojang/renderpearl/api/textures/GpuTextureView;)V",
            at = @At("HEAD"), cancellable = true)
    private void orion$hideReplacedDebugCrosshair(CallbackInfo callback) {
        if (OrionCustomCrosshair.replacesVanilla(OrionClient.getConfigOrNull())) callback.cancel();
    }
}
