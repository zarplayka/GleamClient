package ru.orion.client.legacy.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.orion.client.effect.OrionLegacyZoom;
import ru.orion.client.legacy.OrionLegacyClient;

@Mixin(GameRenderer.class)
abstract class OrionLegacyZoomRendererMixin {
    @Shadow private boolean panoramicMode;

    @Inject(method = "getFov(Lnet/minecraft/client/Camera;FZ)F", at = @At("RETURN"), cancellable = true)
    private void orion$zoom(Camera camera, float partialTick, boolean world, CallbackInfoReturnable<Float> callback) {
        if (panoramicMode) {
            return;
        }
        var config = OrionLegacyClient.getConfigOrNull();
        float factor = world ? OrionLegacyZoom.factor(Minecraft.getInstance(), config)
                : config != null && config.isZoomHand() ? OrionLegacyZoom.current() : 1F;
        if (factor != 1F) {
            callback.setReturnValue(callback.getReturnValueF() / factor);
        }
    }

    @Redirect(method = "bobView(Lcom/mojang/blaze3d/vertex/PoseStack;F)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;lerp(FFF)F"))
    private float orion$scaleBobbing(float partialTick, float previous, float current) {
        float bob = Mth.lerp(partialTick, previous, current);
        var config = OrionLegacyClient.getConfigOrNull();
        return config != null && config.isZoomRelativeBobbing() ? bob / OrionLegacyZoom.current() : bob;
    }
}
