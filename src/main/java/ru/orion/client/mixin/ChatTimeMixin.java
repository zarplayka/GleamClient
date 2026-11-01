package ru.orion.client.mixin;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import ru.orion.client.OrionClient;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

@Mixin(ChatComponent.class)
public abstract class ChatTimeMixin {
    @Unique private static final DateTimeFormatter ORION_TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    @ModifyVariable(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V", at = @At("HEAD"), argsOnly = true)
    private Component orion$timestamp(Component message) {
        var config = OrionClient.getConfigOrNull();
        if (config == null || !config.isChatTimeEnabled()) return message;
        return Component.literal("[" + LocalTime.now().format(ORION_TIME) + "] ").withStyle(ChatFormatting.GRAY).append(message);
    }
}
