package ru.orion.client.effect;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.orion.client.OrionClient;
import ru.orion.client.mixin.OrionChatHistoryAccessor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Сохранение видимых сообщений на диск; файловые операции выполняются вне рендер-потока. */
public final class OrionChatHistory {
    private static final Logger LOGGER = LoggerFactory.getLogger("orion-client");
    private static final ExecutorService IO = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "orion-chat-history");
        thread.setDaemon(true);
        return thread;
    });
    private static boolean manualClear;
    private static boolean loadRequested;
    private static long revision;
    private OrionChatHistory() { }
    public static boolean isManualClear() { return manualClear; }
    public static void clearManually(Runnable clear) {
        manualClear = true;
        revision++;
        try { clear.run(); } finally { manualClear = false; }
        IO.execute(() -> {
            try {
                Path file = path(); Files.createDirectories(file.getParent());
                Files.writeString(file, "", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            } catch (IOException exception) { LOGGER.warn("Не удалось очистить историю чата", exception); }
        });
    }
    public static void ensureLoaded() {
        var config = OrionClient.getConfigOrNull();
        if (config == null || !config.isChatHistoryEnabled() || loadRequested
                || Minecraft.getInstance() == null || Minecraft.getInstance().gui == null
                || Minecraft.getInstance().gui.hud == null) return;
        loadRequested = true;
        long requestedRevision = revision;
        IO.execute(() -> {
            List<Component> restored = new ArrayList<>();
            try {
                Path file = path();
                if (Files.exists(file)) {
                    try (var lines = Files.lines(file, StandardCharsets.UTF_8)) {
                        lines.forEach(line -> {
                            try {
                                JsonElement json = JsonParser.parseString(line);
                                ComponentSerialization.CODEC.parse(JsonOps.INSTANCE, json).result().ifPresent(restored::add);
                            } catch (RuntimeException exception) { LOGGER.warn("Не удалось прочитать строку истории чата", exception); }
                        });
                    }
                }
            } catch (IOException exception) { LOGGER.warn("Не удалось прочитать историю чата", exception); }
            Minecraft.getInstance().execute(() -> restore(restored, requestedRevision));
        });
    }
    private static void restore(List<Component> messages, long requestedRevision) {
        var config = OrionClient.getConfigOrNull();
        if (revision != requestedRevision) return;
        if (config == null || !config.isChatHistoryEnabled()) { loadRequested = false; return; }
        ChatComponent chat = Minecraft.getInstance().gui.hud.getChat();
        List<GuiMessage> existing = ((OrionChatHistoryAccessor) (Object) chat).orion$getMessages();
        Collections.reverse(messages);
        int oldTick = Minecraft.getInstance().gui.hud.getGuiTicks() - 500;
        for (Component message : messages) existing.add(new GuiMessage(oldTick, message, null,
                GuiMessageSource.SYSTEM_CLIENT, GuiMessageTag.systemSinglePlayer()));
        ((OrionChatHistoryAccessor) (Object) chat).orion$refreshMessages();
    }
    public static void record(Component message) {
        var config = OrionClient.getConfigOrNull();
        if (config == null || !config.isChatHistoryEnabled() || manualClear) return;
        ensureLoaded();
        String encoded = ComponentSerialization.CODEC.encodeStart(JsonOps.INSTANCE, message).result()
                .map(JsonElement::toString).orElseGet(() -> new com.google.gson.JsonPrimitive(message.getString()).toString());
        IO.execute(() -> {
            try {
                Path file = path(); Files.createDirectories(file.getParent());
                Files.writeString(file, encoded + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException exception) { LOGGER.warn("Не удалось сохранить сообщение чата", exception); }
        });
    }
    private static Path path() { return FabricLoader.getInstance().getConfigDir().resolve("orion-chat-history.jsonl"); }
}
