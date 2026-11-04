package ru.orion.client.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.orion.client.OrionClient;
import ru.orion.client.effect.OrionZoom;

@Mixin(Camera.class)
public abstract class OrionZoomCameraMixin {
    @Inject(method = "calculateFov(F)F", at = @At("RETURN"), cancellable = true)
    private void orion$worldZoom(float partialTick, CallbackInfoReturnable<Float> callback) {
        float factor = OrionZoom.factor(Minecraft.getInstance(), OrionClient.getConfigOrNull());
        if (factor != 1F && !((Camera) (Object) this).isPanoramicMode()) {
            callback.setReturnValue(callback.getReturnValueF() / factor);
        }
    }

    @Inject(method = "calculateHudFov(F)F", at = @At("RETURN"), cancellable = true)
    private void orion$handZoom(float partialTick, CallbackInfoReturnable<Float> callback) {
        var config = OrionClient.getConfigOrNull();
        if (config != null && config.isZoomHand() && OrionZoom.current() != 1F
                && !((Camera) (Object) this).isPanoramicMode()) {
            callback.setReturnValue(callback.getReturnValueF() / OrionZoom.current());
        }
    }
}
