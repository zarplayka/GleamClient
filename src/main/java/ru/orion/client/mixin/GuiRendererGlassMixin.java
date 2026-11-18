package ru.orion.client.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
//? if >=26.3 {
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;

//?} else {
/*import com.mojang.blaze3d.buffers.GpuBufferSlice;

*///?}
import net.minecraft.client.gui.render.GuiRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.orion.client.hud.OrionGlassCompositor;
import ru.orion.client.OrionClient;

import java.util.function.Supplier;

@Mixin(GuiRenderer.class)
public abstract class GuiRendererGlassMixin {
    //? if >=26.3 {
    @Inject(method = "executeDrawRange(Ljava/util/function/Supplier;Lcom/mojang/blaze3d/pipeline/RenderTarget;Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;II)V",
            at = @At("HEAD"))
    //?} elif >=26.2 {
    /*@Inject(method = "executeDrawRange(Ljava/util/function/Supplier;Lcom/mojang/blaze3d/pipeline/RenderTarget;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;II)V",
            at = @At("HEAD"))
    *///?} else {
    /*@Inject(method = "executeDrawRange", at = @At("HEAD"))
    *///?}
    //? if >=26.2 {
    private void orion$captureGlassBackground(Supplier<String> label, RenderTarget target,
                                            GpuBufferSlice transforms, int from, int to, CallbackInfo callback) {
    //?} else {
    /*private void orion$captureGlassBackground(Supplier<String> label, RenderTarget target,
                                            GpuBufferSlice fog, GpuBufferSlice transforms,
                                            com.mojang.blaze3d.buffers.GpuBuffer indexBuffer,
                                            com.mojang.blaze3d.vertex.VertexFormat.IndexType indexType,
                                            int from, int to, CallbackInfo callback) {
    *///?}
        OrionGlassCompositor.INSTANCE.captureBeforeGui(target.getColorTexture());
    }

    @Inject(method = "close()V", at = @At("HEAD"))
    private void orion$releaseGlass(CallbackInfo callback) {
        var motionBlur = OrionClient.getMotionBlurOrNull();
        if (motionBlur != null) motionBlur.release();
        var mirage = OrionClient.getMirageOrNull();
        if (mirage != null) mirage.release();
        OrionGlassCompositor.INSTANCE.close();
    }
}
