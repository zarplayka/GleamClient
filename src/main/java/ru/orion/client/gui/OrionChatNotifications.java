package ru.orion.client.gui;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;
import ru.orion.client.OrionClient;
import java.awt.image.BufferedImage;

/** Одно уведомление на пакет новых сообщений. */
public final class OrionChatNotifications {
    private static final int WIDTH = 286, HEIGHT = 76;
    private static final long OPEN_NANOS = 220_000_000L, HOLD_NANOS = 4_500_000_000L;
    private static final OrionLumenView VIEW = new OrionLumenView(
            "chat-notification.html", "chat_notification", "/assets/orion-client/fonts/paradox/ParadoxSans-Regular.ttf");
    private static String title = "", detail = "";
    private static long shownAt;
    private static long messageId;
    private static BufferedImage avatar;
    private static String avatarAsset = "";
    private static HitBox hitBox;
    private static JsonObject previous;

    private record HitBox(Screen screen, float left, float top, float right, float bottom) {
        boolean contains(double x, double y) { return x >= left && x < right && y >= top && y < bottom; }
    }

    public static void register() {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, "chat_notification"), (graphics, tick) -> {
            var client = Minecraft.getInstance();
            if (client.gui.screen() == null) render(graphics);
        });
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            ScreenEvents.afterExtract(screen).register((current, graphics, x, y, delta) -> {
                if (client.gui.screen() == current && client.gui.overlay() == null) render(graphics);
            });
            ScreenMouseEvents.allowMouseClick(screen).register((current, event) -> {
                if (event.button() != InputConstants.MOUSE_BUTTON_LEFT || client.gui.overlay() != null
                        || hitBox == null || hitBox.screen() != current || !hitBox.contains(event.x(), event.y())
                        || System.nanoTime() - shownAt >= HOLD_NANOS + OPEN_NANOS) return true;
                var config = OrionClient.getConfigOrNull();
                if (config == null || !config.isChatNotifications()) return true;
                String peer = title;
                clear();
                OrionClickGuiScreen.openChatNotification(client, peer);
                return false;
            });
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> VIEW.dispose());
    }

    public static void show(long id, String messageTitle, String messageDetail) {
        long now = System.nanoTime();
        boolean alreadyVisible = shownAt != 0 && now - shownAt < HOLD_NANOS;
        title = messageTitle;
        detail = messageDetail;
        messageId = id;
        avatar = null;
        avatarAsset = "";
        hitBox = null;
        shownAt = alreadyVisible ? now - OPEN_NANOS : now;
    }

    public static void setAvatar(long id, BufferedImage image) {
        if (shownAt != 0 && messageId == id && image != null) avatar = image;
    }

    public static void clear() {
        shownAt = 0;
        title = detail = "";
        avatar = null;
        avatarAsset = "";
        hitBox = null;
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
        int y = Math.max(0, window.getGuiScaledHeight() - height - 7);
        VIEW.resize(WIDTH, HEIGHT, 1F);
        if (avatar != null && avatarAsset.isEmpty()) avatarAsset = OrionSiteMenu.registerAvatar(avatar);
        var state = new JsonObject();
        state.addProperty("title", title);
        state.addProperty("detail", detail);
        state.addProperty("avatar", avatarAsset);
        state.addProperty("light", config.getClickGuiTheme() == 4);
        state.addProperty("accent", config.getClickGuiAccent());
        if (!state.equals(previous)) {
            VIEW.update(state);
            previous = state;
        }
        float phase = Math.clamp(Math.min(age, HOLD_NANOS + OPEN_NANOS - age) / (float)OPEN_NANOS, 0F, 1F);
        VIEW.opening(phase);
        VIEW.frame();
        boolean drawn = VIEW.draw(graphics, x, y, width, height, scale, scale);
        float slide = (1F - phase) * (1F - phase) * (1F - phase) * 18F;
        hitBox = drawn && phase >= .4F ? new HitBox(Minecraft.getInstance().gui.screen(), x + 4 * scale, y + (4 + slide) * scale,
                x + (WIDTH - 4) * scale, Math.min(y + height, y + (64 + slide) * scale)) : null;
    }

    private OrionChatNotifications() { }
}
