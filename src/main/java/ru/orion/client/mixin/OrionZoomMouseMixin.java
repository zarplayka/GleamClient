package ru.orion.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.orion.client.OrionClient;
import ru.orion.client.effect.OrionZoom;

@Mixin(MouseHandler.class)
public abstract class OrionZoomMouseMixin {
    @Shadow @Final private Minecraft minecraft;

    @Inject(method = "onScroll(JDD)V", at = @At("HEAD"), cancellable = true)
    private void orion$scrollZoom(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (window != 0L && window == minecraft.getWindow().handle()
                && OrionZoom.scroll(minecraft, OrionClient.getConfigOrNull(), vertical)) {
            ci.cancel();
        }
    }
}
