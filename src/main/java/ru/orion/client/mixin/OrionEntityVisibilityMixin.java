package ru.orion.client.mixin;

import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.orion.client.effect.OrionNoRender;

@Mixin(EntityRenderDispatcher.class)
public abstract class OrionEntityVisibilityMixin {
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void orion$hideEntity(Entity entity, net.minecraft.client.renderer.culling.Frustum frustum,
                                  double x, double y, double z,
                                  //? if >=26.3 {
                                  float partialTick,
                                  //?}
                                  CallbackInfoReturnable<Boolean> callback) {
        if (OrionNoRender.hidesEntity(entity)) callback.setReturnValue(false);
    }
}
