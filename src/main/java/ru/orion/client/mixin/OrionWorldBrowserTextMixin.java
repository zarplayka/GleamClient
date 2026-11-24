package ru.orion.client.mixin;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.CharacterEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.orion.client.gui.OrionWorldBrowser;

@Mixin(KeyboardHandler.class)
abstract class OrionWorldBrowserTextMixin {
    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
    private void gleam$worldBrowserText(long window, CharacterEvent event, CallbackInfo ci) {
        if (window == Minecraft.getInstance().getWindow().handle() && OrionWorldBrowser.type(event)) ci.cancel();
    }
}
