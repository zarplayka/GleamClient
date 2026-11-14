package ru.orion.client.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.resources.Identifier;
import ru.orion.client.GleamMinecraft;
import ru.orion.client.OrionClient;
import ru.orion.client.config.OrionAccounts;

import java.awt.image.BufferedImage;

final class OrionWorldInvitation {
    private static final int WIDTH = 360, HEIGHT = 86;
    private static final long MOTION = 230_000_000L, HOLD = 830_000_000L, TTL = 15_000_000_000L;
    private static final OrionLumenView VIEW = new OrionLumenView(
            "world-invite.html", "world_invite", "/assets/orion-client/fonts/paradox/ParadoxSans-Regular.ttf");
    private static JsonObject invite, lastState;
    private static String seen = "";
    private static long shownAt, expires, closing, startedHold, stoppedHold;
    private static double releasedProgress;
    private static boolean wasDown;
    private static BufferedImage avatar;
    private static String asset = "";
    private static HitBox hit;

    private record HitBox(Screen screen, float x, float y, float scale) {}

    private OrionWorldInvitation() {}

    static void register() {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, "world_invite"), (graphics, tick) -> {
            if (GleamMinecraft.screen(Minecraft.getInstance()) == null) render(graphics);
        });
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            ScreenEvents.afterExtract(screen).register((current, graphics, mx, my, delta) -> {
                if (GleamMinecraft.screen(client) == current && GleamMinecraft.overlay(client) == null) render(graphics);
            });
            ScreenMouseEvents.allowMouseClick(screen).register((current, event) -> {
                if (hit == null || hit.screen() != current || invite == null || closing != 0
                        || event.button() != InputConstants.MOUSE_BUTTON_LEFT) return true;
                double x = (event.x() - hit.x()) / hit.scale(), y = (event.y() - hit.y()) / hit.scale();
                if (x >= 330 && x < 354 && y >= 1 && y < 21) {
                    close(System.nanoTime());
                    return false;
                }
                return true;
            });
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> VIEW.dispose());
    }

    static void sync(Minecraft client, JsonArray items) {
        JsonObject incoming = null;
        for (var element : items) {
            var item = element.getAsJsonObject();
            if (item.get("incoming").getAsBoolean() && item.get("state").getAsString().equals("pending"))
                incoming = item;
        }
        long now = System.nanoTime();
        if (incoming == null) {
            if (invite != null && closing == 0) close(now);
            return;
        }
        String id = incoming.get("id").getAsString();
        if (id.equals(seen)) return;
        seen = id;
        invite = incoming;
        expires = now + Math.min(TTL, incoming.get("remaining").getAsLong() * 1_000_000L);
        shownAt = now;
        closing = startedHold = stoppedHold = 0;
        wasDown = keyDown(client);
        avatar = null;
        asset = "";
        var publicId = invite.get("public_id");
        if (publicId != null && !publicId.isJsonNull())
            OrionAccounts.INSTANCE.publicAvatar(publicId.getAsString()).thenAccept(image -> client.execute(() -> {
                if (invite != null && id.equals(invite.get("id").getAsString())) avatar = image;
            }));
    }

    static void tick(Minecraft client) {
        if (invite == null) return;
        long now = System.nanoTime();
        if (closing != 0) {
            if (now - closing >= MOTION) invite = null;
            return;
        }
        if (now >= expires) {
            close(now);
            return;
        }
        var screen = GleamMinecraft.screen(client);
        boolean down = keyDown(client);
        boolean usable = client.isWindowActive() && GleamMinecraft.overlay(client) == null && (screen == null || screen instanceof TitleScreen)
                && SharedConstants.getCurrentVersion().name().equals(invite.get("version").getAsString());
        if (down && !wasDown && usable) startedHold = now;
        wasDown = down;
        if (startedHold != 0 && (!down || !usable)) {
            releasedProgress = Math.clamp((now - startedHold) / (double) HOLD, 0, 1);
            stoppedHold = now;
            startedHold = 0;
        }
        if (startedHold != 0 && now - startedHold >= HOLD) {
            String id = invite.get("id").getAsString();
            close(now);
            OrionFriendWorlds.accept(client, id);
        }
    }

    private static boolean keyDown(Minecraft client) {
        if (!client.isWindowActive()) return false;
        //? if >=26.3 {
        return InputConstants.isKeyDown(InputConstants.KEY_Y);
        //?} else {
        /*return InputConstants.isKeyDown(client.getWindow(), InputConstants.KEY_Y);
        *///?}
    }

    private static void close(long now) {
        closing = now;
        hit = null;
    }

    static void clear() {
        invite = lastState = null;
        avatar = null;
        asset = seen = "";
        hit = null;
    }

    private static void render(GuiGraphicsExtractor graphics) {
        if (invite == null || !VIEW.ready()) return;
        var client = Minecraft.getInstance();
        var window = client.getWindow();
        float scale = Math.clamp(window.getWidth() / 1600F, .75F, 2F) / window.getGuiScale();
        int width = (int) Math.ceil(WIDTH * scale), height = (int) Math.ceil(HEIGHT * scale);
        int x = Math.max(0, window.getGuiScaledWidth() - width - 7), y = 7;
        VIEW.resize(WIDTH, HEIGHT, 1F);
        if (avatar != null && asset.isEmpty()) asset = OrionSiteMenu.registerAvatar(avatar);
        String user = invite.get("username").getAsString();
        String version = invite.get("version").getAsString();
        boolean compatible = version.equals(SharedConstants.getCurrentVersion().name());
        long now = System.nanoTime();
        long sample = closing == 0 ? now : closing;
        var config = OrionClient.getConfigOrNull();
        var state = new JsonObject();
        state.addProperty("title", user + " " + OrionGuiLanguage.translate("приглашает в мир"));
        state.addProperty("world", invite.get("world").getAsString() + " · Minecraft " + version);
        state.addProperty("hint", OrionGuiLanguage.translate(compatible ? "Удерживай Y, чтобы принять" : "Открой приглашение в лаунчере"));
        state.addProperty("key", compatible);
        state.addProperty("avatar", asset);
        state.addProperty("initial", user.substring(0, 1).toUpperCase(java.util.Locale.ROOT));
        state.addProperty("light", config != null && config.getClickGuiTheme() == 4);
        state.addProperty("remaining", Math.clamp((expires - sample) / (double)TTL, 0, 1));
        state.addProperty("hold", startedHold != 0 ? Math.clamp((sample - startedHold) / (double) HOLD, 0, 1)
                : releasedProgress * Math.clamp(1 - (sample - stoppedHold) / 160_000_000D, 0, 1));
        double enter = Math.clamp((sample - shownAt) / (double) MOTION, 0, 1);
        double exit = closing == 0 ? 0 : Math.clamp((now - closing) / (double) MOTION, 0, 1);
        state.addProperty("opacity", (1 - Math.pow(1 - enter, 3)) * Math.pow(1 - exit, 2));
        state.addProperty("offset", 5 * (1 - enter) - 6 * exit);
        if (!state.equals(lastState)) {
            VIEW.update(state);
            lastState = state;
        }
        VIEW.frame();
        if (VIEW.draw(graphics, x, y, width, height, scale, scale))
            hit = new HitBox(GleamMinecraft.screen(client), x, y, scale);
    }
}
