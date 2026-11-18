package ru.orion.client.legacy.mixin;

import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.client.GuiMessageTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.orion.client.legacy.OrionLegacyClient;
import ru.orion.client.effect.OrionLegacyChatRules;

@Mixin(ChatComponent.class)
public abstract class OrionLegacyChatRulesMixin {
    @Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V", at = @At("HEAD"), cancellable = true)
    private void orion$filter(Component text, MessageSignature signature, GuiMessageTag tag, CallbackInfo ci) {
        if (OrionLegacyChatRules.shouldHide(text, OrionLegacyClient.getConfigOrNull())) ci.cancel();
    }

    @ModifyVariable(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V", at = @At("HEAD"), argsOnly = true)
    private Component orion$highlight(Component text) {
        return OrionLegacyChatRules.highlight(text, OrionLegacyClient.getConfigOrNull());
    }
}
