package ru.orion.client.mixin;

import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.orion.client.OrionClient;

@Mixin(LevelRenderer.class)
public abstract class BlockOutlineMixin {
    @Inject(method = "submitBlockOutline(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/LevelRenderState;)V", at = @At("HEAD"), cancellable = true)
    private void orion$hideVanillaOutline(CallbackInfo ci) {
        var config = OrionClient.getConfigOrNull();
        if (config != null && config.isBlockHighlightEnabled()) ci.cancel();
    }
}
