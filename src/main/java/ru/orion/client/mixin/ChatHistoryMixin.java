package ru.orion.client.mixin;

import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import ru.orion.client.OrionClient;
import ru.orion.client.effect.OrionChatHistory;

import java.util.List;

@Mixin(ChatComponent.class)
public abstract class ChatHistoryMixin {
    @Shadow @Final private List<GuiMessage> allMessages;
    @Shadow @Final private List<GuiMessage.Line> trimmedMessages;

    @Redirect(method = "clearMessages(Z)V", at = @At(value = "INVOKE", target = "Ljava/util/List;clear()V"))
    private void orion$keepChat(List<?> list) {
        var config = OrionClient.getConfigOrNull();
        if (config != null && config.isChatHistoryEnabled() && !OrionChatHistory.isManualClear()
                && (list == allMessages || list == trimmedMessages)) return;
        list.clear();
    }
    @Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/ChatComponent;logChatMessage(Lnet/minecraft/client/multiplayer/chat/GuiMessage;)V", shift = At.Shift.AFTER))
    private void orion$persistMessage(Component message, MessageSignature signature, GuiMessageSource source,
                                      GuiMessageTag tag, CallbackInfo callback) {
        OrionChatHistory.record(message);
    }
    @ModifyConstant(method = {"addMessageToDisplayQueue", "addMessageToQueue"}, constant = @Constant(intValue = 100))
    private int orion$historyLimit(int original) {
        var config = OrionClient.getConfigOrNull();
        return config != null && config.isChatHistoryEnabled() ? Integer.MAX_VALUE : original;
    }
}
