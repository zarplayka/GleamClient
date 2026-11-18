package ru.orion.client.mixin;

import net.minecraft.client.renderer.WorldBorderRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.orion.client.effect.OrionNoRender;

@Mixin(WorldBorderRenderer.class)
public abstract class OrionNoRenderBorderMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void orion$hideBorder(CallbackInfo callback) {
        if (OrionNoRender.hides(OrionNoRender.Option.WORLD_BORDER)) callback.cancel();
    }
}
