package ru.orion.client.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.orion.client.replay.GleamReplay;

@Mixin(KeyboardHandler.class)
abstract class GleamReplayKeyboardMixin {
    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void gleam$replayShortcut(long window, int action, KeyEvent event, CallbackInfo ci) {
        var client = Minecraft.getInstance();
        if (action == InputConstants.PRESS && window == client.getWindow().handle() &&
                GleamReplay.INSTANCE.hotkey(client, event)) ci.cancel();
    }
}
