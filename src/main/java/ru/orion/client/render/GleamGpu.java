package ru.orion.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
//? if >=26.3 {
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
//?} else {
/*import com.mojang.blaze3d.pipeline.RenderPipeline;
*///?}

/** Отличия подготовки GPU-пайплайнов между поколениями рендера Minecraft. */
public final class GleamGpu {
    private GleamGpu() { }
    //? if >=26.3 {
    public static CompiledRenderPipeline pipelineOrNull(RenderPipeline pipeline) {
        return RenderSystem.getCompiledPipelineNullable(pipeline);
    }
    //?} else {
        /*public static RenderPipeline pipelineOrNull(RenderPipeline pipeline) {
        return RenderSystem.getDevice().precompilePipeline(pipeline).isValid() ? pipeline : null;
    }
    *///?}
}
