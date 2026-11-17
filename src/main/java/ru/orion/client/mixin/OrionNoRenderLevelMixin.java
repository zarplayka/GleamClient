package ru.orion.client.mixin;

import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.orion.client.effect.OrionNoRender;

import static ru.orion.client.effect.OrionNoRender.Option.*;

@Mixin(LevelRenderer.class)
public abstract class OrionNoRenderLevelMixin {
    @Inject(method = "addSkyPass", at = @At("HEAD"), cancellable = true)
    private void orion$sky(CallbackInfo callback) {
        if (OrionNoRender.hides(SKY)) callback.cancel();
    }

    //? if <26.3 {
    /*@Inject(method = "addCloudsPass", at = @At("HEAD"), cancellable = true)
    private void orion$clouds(CallbackInfo callback) {
        if (OrionNoRender.hides(CLOUDS)) callback.cancel();
    }
    *///?}

    @Inject(method = "submitBlockEntities", at = @At("HEAD"), cancellable = true)
    private void orion$blockEntities(CallbackInfo callback) {
        if (OrionNoRender.hides(BLOCK_ENTITIES)) callback.cancel();
    }
}
