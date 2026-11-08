package ru.orion.client.gui;

import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import ru.orion.client.OrionClient;

/** Одно уведомление на пакет новых сообщений. */
public final class OrionChatNotifications {
    private static final int WIDTH = 286, HEIGHT = 76;
    private static final long OPEN_NANOS = 220_000_000L, HOLD_NANOS = 4_500_000_000L;
    private static final OrionLumenView VIEW = new OrionLumenView(
            "chat-notification.html", "chat_notification", "/assets/orion-client/fonts/paradox/ParadoxSans-Regular.ttf");
    private static String title = "", detail = "";
    private static long shownAt;
    private static JsonObject previous;

    public static void register() {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, "chat_notification"), (graphics, tick) -> {
            var client = Minecraft.getInstance();
            if (!(client.gui.screen() instanceof OrionClickGuiScreen)) render(graphics);
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> VIEW.dispose());
    }

    public static void show(String messageTitle, String messageDetail) {
        long now = System.nanoTime();
        boolean alreadyVisible = shownAt != 0 && now - shownAt < HOLD_NANOS;
        title = messageTitle;
        detail = messageDetail;
        shownAt = alreadyVisible ? now - OPEN_NANOS : now;
    }

    public static void clear() {
        shownAt = 0;
        title = detail = "";
        previous = null;
    }

    static void render(GuiGraphicsExtractor graphics) {
        if (shownAt == 0) return;
        long age = System.nanoTime() - shownAt;
        var config = OrionClient.getConfigOrNull();
        if (age >= HOLD_NANOS + OPEN_NANOS || config == null || !config.isChatNotifications()) {
            clear();
            return;
        }
        if (!VIEW.ready()) return;
        var window = Minecraft.getInstance().getWindow();
        float scale = Math.clamp(window.getWidth() / 1920F, .75F, 2F) / window.getGuiScale();
        int width = Math.max(1, (int)Math.ceil(WIDTH * scale)), height = Math.max(1, (int)Math.ceil(HEIGHT * scale));
        int x = Math.max(0, window.getGuiScaledWidth() - width - 7);
        int y = 7;
        VIEW.resize(WIDTH, HEIGHT, 1F);
        var state = new JsonObject();
        state.addProperty("title", title);
        state.addProperty("detail", detail);
        state.addProperty("light", config.getClickGuiTheme() == 4);
        state.addProperty("accent", config.getClickGuiAccent());
        if (!state.equals(previous)) {
            VIEW.update(state);
            previous = state;
        }
        float phase = Math.clamp(Math.min(age, HOLD_NANOS + OPEN_NANOS - age) / (float)OPEN_NANOS, 0F, 1F);
        VIEW.opening(phase);
        VIEW.frame();
        VIEW.draw(graphics, x, y, width, height, scale, scale);
    }

    private OrionChatNotifications() { }
}
