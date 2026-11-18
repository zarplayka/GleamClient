package ru.orion.client.legacy.mixin;

import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Slice;
import ru.orion.client.legacy.OrionLegacyClient;

@Mixin(LightTexture.class)
abstract class OrionLegacyFullBrightMixin {
    @ModifyArg(method = "updateLightTexture(F)V",
            slice = @Slice(from = @At(value = "CONSTANT", args = "stringValue=NightVisionFactor")),
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/shaders/AbstractUniform;set(F)V", ordinal = 0))
    private float orion$fullBright(float vanillaFactor) {
        var config = OrionLegacyClient.getConfigOrNull();
        return config != null && config.isFullBrightEnabled() ? 1F : vanillaFactor;
    }
}
