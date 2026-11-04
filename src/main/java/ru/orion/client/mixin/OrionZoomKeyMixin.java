package ru.orion.client.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.orion.client.effect.OrionZoom;
import ru.orion.client.keybind.OrionKeybinds;

@Mixin(KeyMapping.class)
public abstract class OrionZoomKeyMixin {
    @Inject(method = "set(Lcom/mojang/blaze3d/platform/InputConstants$Key;Z)V", at = @At("HEAD"))
    private static void orion$zoomKey(InputConstants.Key key, boolean pressed, CallbackInfo ci) {
        KeyMapping zoom = OrionKeybinds.getZoomKey();
        if (zoom != null && zoom.matches(key)) OrionZoom.keyEvent(pressed);
    }
}
