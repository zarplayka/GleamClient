package ru.orion.client.mixin;

//? if >=26.3 {
import com.mojang.renderpearl.api.pipeline.RenderPipeline;

//?} else {
/*import com.mojang.blaze3d.pipeline.RenderPipeline;

*///?}
import net.minecraft.client.renderer.RenderPipelines;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(RenderPipelines.class)
public interface RenderPipelinesAccessor {
    @Accessor("GUI_SNIPPET")
    static RenderPipeline.Snippet orion$getGuiSnippet() {
        throw new AssertionError();
    }

    @Invoker("register")
    static RenderPipeline orion$register(RenderPipeline pipeline) {
        throw new AssertionError();
    }

    //? if >=26.3 {
    @Invoker("registerOptional")
    static RenderPipeline orion$registerOptional(RenderPipeline pipeline) {
        throw new AssertionError();
    }
    //?} else {
        /*@Invoker("register")
    static RenderPipeline orion$registerOptional(RenderPipeline pipeline) {
        throw new AssertionError();
    }
    *///?}

}
