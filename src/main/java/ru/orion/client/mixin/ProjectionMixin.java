package ru.orion.client.mixin;

import net.minecraft.client.renderer.Projection;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.orion.client.OrionClient;
import ru.orion.client.config.OrionConfig;
import ru.orion.client.effect.OrionZoom;

@Mixin(Projection.class)
public class ProjectionMixin {
    @Inject(method = "getMatrix", at = @At("RETURN"))
    private void orion$applyAspect(Matrix4f dest, CallbackInfoReturnable<Matrix4f> cir) {
        Projection self = (Projection) (Object) this;
        if (self.fov() <= 0.0F) {
            return;
        }

        OrionConfig config = OrionClient.getConfigOrNull();
        if (config == null) {
            return;
        }

        Matrix4f projection = cir.getReturnValue();
        float zoom = OrionZoom.factor(config);
        projection.m00(projection.m00() * zoom);
        projection.m11(projection.m11() * zoom);
        if (config.isAspectEnabled()) {
            float stretch = config.getAspectStretch();
            if (stretch > 0.0F && stretch != 1.0F) projection.m00(projection.m00() / stretch);
        }
    }
}
