package ru.orion.client.mixin;

//? if >=26.3 {
import com.mojang.renderpearl.api.pipeline.ShaderSource;

//?} else {
/*import com.mojang.blaze3d.shaders.ShaderSource;

*///?}
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import ru.orion.client.gui.OrionTitleMenu;

@Mixin(GameRenderer.class)
abstract class OrionStartupShadersMixin {
    //? if >=26.3 {
    @ModifyArg(method = "preloadUiShader", at = @At(value = "INVOKE", target =
            "Lcom/mojang/blaze3d/pipeline/PipelineCache;<init>(Lcom/mojang/renderpearl/api/device/GpuDevice;Lcom/mojang/renderpearl/api/pipeline/ShaderSource;)V"), index = 1)
    //?} else {
    /*@ModifyArg(method = "preloadUiShader", at = @At(value = "INVOKE", target =
            "Lcom/mojang/blaze3d/systems/GpuDevice;precompilePipeline(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lcom/mojang/blaze3d/shaders/ShaderSource;)Lcom/mojang/blaze3d/pipeline/CompiledRenderPipeline;"), index = 1)
    *///?}
    private static ShaderSource orion$loadStartupShaders(ShaderSource vanilla) {
        return OrionTitleMenu.startupShaders(vanilla);
    }
}
