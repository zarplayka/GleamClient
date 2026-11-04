package ru.orion.client.mixin;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.orion.client.OrionClient;
import ru.orion.client.effect.OrionZoom;
import ru.orion.client.keybind.OrionKeybinds;

@Mixin(Hud.class)
public abstract class OrionZoomHudMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V",
            at = @At("HEAD"), cancellable = true)
    private void orion$hideHudForZoom(GuiGraphicsExtractor context, DeltaTracker tracker, CallbackInfo callback) {
        var config = OrionClient.getConfigOrNull();
        if (config != null && config.isZoomEnabled() && config.isSecondaryZoomHideHud()
                && OrionZoom.secondary() && OrionKeybinds.isSecondaryZoomDown()
                && Minecraft.getInstance().gui.screen() == null) callback.cancel();
    }
}
