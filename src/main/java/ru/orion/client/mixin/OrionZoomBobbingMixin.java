package ru.orion.client.mixin;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import ru.orion.client.OrionClient;
import ru.orion.client.effect.OrionZoom;

@Mixin(GameRenderer.class)
public abstract class OrionZoomBobbingMixin {
    @Redirect(method = "bobView", at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/renderer/state/level/CameraEntityRenderState;bob:F"))
    private float orion$scaleBobbing(CameraEntityRenderState state) {
        var config = OrionClient.getConfigOrNull();
        return config != null && config.isZoomRelativeBobbing() ? state.bob / OrionZoom.current() : state.bob;
    }
}
