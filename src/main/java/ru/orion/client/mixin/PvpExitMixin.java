package ru.orion.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.orion.client.effect.OrionPvpSafe;
import ru.orion.client.gui.OrionPvpExitScreen;

@Mixin(PauseScreen.class)
public abstract class PvpExitMixin {
    @Shadow private Button disconnectButton;
    @Inject(method = "lambda$createPauseMenu$11()V", at = @At("HEAD"), cancellable = true)
    private void orion$confirmExit(CallbackInfo callback) {
        if (OrionPvpSafe.remainingSeconds() <= 0) return;
        if (disconnectButton != null) disconnectButton.active = true;
        Minecraft.getInstance().gui.setScreen(new OrionPvpExitScreen((Screen) (Object) this));
        callback.cancel();
    }
}
