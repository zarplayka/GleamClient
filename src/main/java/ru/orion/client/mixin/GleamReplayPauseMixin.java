package ru.orion.client.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.orion.client.replay.GleamReplay;

@Mixin(PauseScreen.class)
abstract class GleamReplayPauseMixin {
    @Inject(method = "createPauseMenu", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/chat/CommonComponents;disconnectButtonLabel(Z)Lnet/minecraft/network/chat/Component;"))
    private void gleam$addReplays(CallbackInfo ci, @Local GridLayout.RowHelper rows) {
        if (GleamReplay.available()) {
            rows.addChild(Button.builder(Component.translatable(GleamReplay.LABEL),
                    button -> GleamReplay.INSTANCE.open(Minecraft.getInstance())).width(204).build(), 2);
        }
    }
}
