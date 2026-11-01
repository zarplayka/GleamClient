package ru.orion.client.mixin;

import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

@Mixin(ChatComponent.class)
public interface OrionChatHistoryAccessor {
    @Accessor("allMessages") List<GuiMessage> orion$getMessages();
    @Invoker("refreshTrimmedMessages") void orion$refreshMessages();
}
