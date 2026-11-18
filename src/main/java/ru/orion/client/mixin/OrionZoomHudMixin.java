//~ legacy_gui
package ru.orion.client.mixin;

import ru.orion.client.GleamMinecraft;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
//? if >=26.2 {
import net.minecraft.client.gui.Hud;
//?} else {
/*import net.minecraft.client.gui.Gui;
*///?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.orion.client.OrionClient;
import ru.orion.client.effect.OrionZoom;
import ru.orion.client.keybind.OrionKeybinds;

//? if >=26.2 {
@Mixin(Hud.class)
//?} else {
/*@Mixin(Gui.class)
*///?}
public abstract class OrionZoomHudMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V",
            at = @At("HEAD"), cancellable = true)
    private void orion$hideHudForZoom(GuiGraphicsExtractor context, DeltaTracker tracker, CallbackInfo callback) {
        var config = OrionClient.getConfigOrNull();
        if (config != null && config.isZoomEnabled() && config.isSecondaryZoomHideHud()
                && OrionZoom.secondary() && OrionKeybinds.isSecondaryZoomDown()
                && GleamMinecraft.screen(Minecraft.getInstance()) == null) callback.cancel();
    }
}
