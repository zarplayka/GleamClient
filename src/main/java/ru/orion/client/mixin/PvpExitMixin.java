package ru.orion.client.mixin;

import ru.orion.client.GleamMinecraft;

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

    //? if >=26.3 {
    @Inject(method = "lambda$createPauseMenu$11()V", at = @At("HEAD"), cancellable = true)
    //?} elif >=26.2 {
    /*@Inject(method = "lambda$createPauseMenu$12()V", at = @At("HEAD"), cancellable = true)
    *///?} else {
    /*@Inject(method = "lambda$createPauseMenu$7()V", at = @At("HEAD"), cancellable = true)
    *///?}
    private void orion$confirmExit(CallbackInfo callback) {
        if (OrionPvpSafe.remainingSeconds() <= 0) return;
        if (disconnectButton != null) disconnectButton.active = true;
        GleamMinecraft.setScreen(Minecraft.getInstance(), new OrionPvpExitScreen((Screen) (Object) this));
        callback.cancel();
    }
}
