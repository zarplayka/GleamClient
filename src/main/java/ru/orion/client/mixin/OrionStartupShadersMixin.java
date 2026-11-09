package ru.orion.client.mixin;

import com.mojang.renderpearl.api.pipeline.ShaderSource;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import ru.orion.client.gui.OrionTitleMenu;

@Mixin(GameRenderer.class)
abstract class OrionStartupShadersMixin {
    @ModifyArg(method = "preloadUiShader", at = @At(value = "INVOKE", target =
            "Lcom/mojang/blaze3d/pipeline/PipelineCache;<init>(Lcom/mojang/renderpearl/api/device/GpuDevice;Lcom/mojang/renderpearl/api/pipeline/ShaderSource;)V"), index = 1)
    private static ShaderSource orion$loadStartupShaders(ShaderSource vanilla) {
        return OrionTitleMenu.startupShaders(vanilla);
    }
}
