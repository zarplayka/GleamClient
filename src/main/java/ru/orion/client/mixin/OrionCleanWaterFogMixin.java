package ru.orion.client.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.world.level.material.FogType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.orion.client.effect.OrionCleanWater;

@Mixin(FogRenderer.class)
public abstract class OrionCleanWaterFogMixin {
    @Inject(method = "getFogType", at = @At("RETURN"), cancellable = true)
    private void orion$clearWaterFog(Camera camera, CallbackInfoReturnable<FogType> cir) {
        if (cir.getReturnValue() == FogType.WATER && OrionCleanWater.clearsFog()) {
            cir.setReturnValue(FogType.ATMOSPHERIC);
        }
    }
}
