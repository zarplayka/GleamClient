package ru.orion.client.mixin;

import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.orion.client.effect.OrionNoRender;

import static ru.orion.client.effect.OrionNoRender.Option.*;

@Mixin(GameRenderer.class)
public abstract class OrionNoRenderCameraMixin {
    @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
    private void orion$hurtTilt(CallbackInfo callback) {
        if (OrionNoRender.hides(HURT_CAMERA)) callback.cancel();
    }

    @Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
    private void orion$hand(CallbackInfo callback) {
        if (OrionNoRender.hides(HAND)) callback.cancel();
    }
}
