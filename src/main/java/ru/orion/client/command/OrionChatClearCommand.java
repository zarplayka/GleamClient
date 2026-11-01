package ru.orion.client.command;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.minecraft.client.Minecraft;
import ru.orion.client.effect.OrionChatHistory;

/** Ручная очистка чата, включая сохранённые сообщения. */
public final class OrionChatClearCommand {
    public void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registry) -> dispatcher.register(
                ClientCommands.literal("orionchatclear").executes(context -> {
                    var chat = Minecraft.getInstance().gui.hud.getChat();
                    OrionChatHistory.clearManually(() -> chat.clearMessages(false));
                    return 1;
                })));
    }
}
