package ru.orion.client.legacy.mixin;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.orion.client.legacy.OrionLegacyClient;
import ru.orion.client.effect.OrionLegacyZoom;

@Mixin(Gui.class)
public abstract class OrionLegacyZoomHudMixin {
    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V",
            at = @At("HEAD"), cancellable = true)
    private void orion$hideHudForZoom(GuiGraphics context, DeltaTracker tracker, CallbackInfo callback) {
        var config = OrionLegacyClient.getConfigOrNull();
        if (config != null && config.isZoomEnabled() && config.isSecondaryZoomHideHud()
                && OrionLegacyZoom.secondary() && OrionLegacyZoom.isSecondaryDown()
                && Minecraft.getInstance().screen == null) callback.cancel();
    }
}
