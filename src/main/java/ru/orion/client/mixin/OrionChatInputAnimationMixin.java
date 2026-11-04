package ru.orion.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.orion.client.OrionClient;

/** Анимация поля ввода на основе ChatAnimation от Ezzenix (MIT). */
@Mixin(ChatScreen.class)
public abstract class OrionChatInputAnimationMixin {
    @Unique private long orion$opened;
    @Unique private float orion$offset;

    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void orion$inputOffset(GuiGraphicsExtractor graphics, int x, int y, float delta, CallbackInfo callback) {
        orion$offset = 0F;
        var config = OrionClient.getConfigOrNull();
        Minecraft client = Minecraft.getInstance();
        if (config == null || !config.isChatAnimationEnabled() || !config.isChatAnimationInput()
                || client.player == null || client.player.isSleeping()) return;
        long now = System.nanoTime();
        if (orion$opened == 0L) orion$opened = now;
        float t = 1F - Math.clamp((now - orion$opened) / (config.getChatAnimationInputTime() * 1_000_000F), 0F, 1F);
        orion$offset = t * t * (2.70158F * t - 1.70158F) * 8F * client.getWindow().getHeight() / 1080F;
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void orion$resetInput(CallbackInfo callback) {
        orion$opened = 0L;
    }

    @Inject(method = "extractRenderState", at = {
            @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V"),
            @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/Screen;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V")
    })
    private void orion$beginInput(GuiGraphicsExtractor graphics, int x, int y, float delta, CallbackInfo callback) {
        if (orion$offset == 0F) return;
        graphics.pose().pushMatrix();
        graphics.pose().translate(0F, orion$offset);
    }

    @Inject(method = "extractRenderState", at = {
            @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V", shift = At.Shift.AFTER),
            @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/Screen;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V", shift = At.Shift.AFTER)
    })
    private void orion$endInput(GuiGraphicsExtractor graphics, int x, int y, float delta, CallbackInfo callback) {
        if (orion$offset != 0F) graphics.pose().popMatrix();
    }
}
