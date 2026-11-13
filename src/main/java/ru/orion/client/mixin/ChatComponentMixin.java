package ru.orion.client.mixin;

import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import ru.orion.client.effect.OrionFakeStaffList;

/** Дополняет серверный список администрации до разбиения сообщения на строки. */
@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {
    @ModifyVariable(
            //? if >=26.1 {
            method = {"addServerSystemMessage", "addClientSystemMessage"},
            //?} else {
            /*method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V",
            *///?}
            at = @At("HEAD"),
            argsOnly = true
    )
    private Component orion$addConfiguredStaff(Component message) {
        return OrionFakeStaffList.addConfiguredPlayers(message);
    }
}
