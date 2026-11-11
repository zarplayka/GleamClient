package ru.orion.client.media;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import com.paradox.lumen.LumenNative;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.resources.Identifier;
import org.slf4j.LoggerFactory;
import ru.orion.client.OrionClient;
import ru.orion.client.config.OrionConfig;
import ru.orion.client.config.OrionConfigManager;
import ru.orion.client.effect.OrionNameProtect;
import ru.orion.client.gui.OrionClickGuiScreen;
import ru.orion.client.gui.OrionGuiLanguage;
import ru.orion.client.gui.OrionLumenView;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Мост острова HUD: HTML/CSS/JS через Lumen, данные и команды остаются в менеджерах Java. */
public final class OrionMediaPlayerHud {
    private static final String ROOT = "/assets/orion-client/";
    private static final String VERSION = FabricLoader.getInstance().getModContainer(OrionClient.MOD_ID)
            .map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse("");
    private static byte[] document;
    private final OrionConfigManager manager;
    private final OrionMediaController controller;
    private final OrionLumenView view = new OrionLumenView("hud-island/index.html", "hud_island",
            ROOT + "fonts/paradox/ParadoxSans-Regular.ttf", OrionMediaPlayerHud::loadDocument);
    private JsonObject previous;
    private JsonObject labels;
    private String language;
    private Bounds bounds;
    private boolean captured, visible;
    private long renderedAt;
    private int coverGeneration = -1;
    private String coverAsset = "";
    private long lyricsRevision = -1;
    private String lyricsStatus = "";

    private record Bounds(float x, float y, float width, float height, float radius) {
        boolean contains(float px, float py) {
            if (px < x || px > x + width || py < y || py > y + height) return false;
            float dx = Math.max(Math.max(x + radius - px, px - (x + width - radius)), 0);
            float dy = Math.max(py - (y + height - radius), 0);
            return dx * dx + dy * dy <= radius * radius;
        }
    }

    public OrionMediaPlayerHud(OrionConfigManager manager, OrionMediaController controller) {
        this.manager = manager;
        this.controller = controller;
    }

    public void register() {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, "media_player"),
                (graphics, delta) -> {
                    if (!(Minecraft.getInstance().gui.screen() instanceof ChatScreen)) render(graphics);
                });
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!(screen instanceof ChatScreen)) return;
            ScreenEvents.afterExtract(screen).register((current, graphics, x, y, delta) -> render(graphics));
            ScreenMouseEvents.allowMouseClick(screen).register((current, event) -> {
                if (event.button() != InputConstants.MOUSE_BUTTON_LEFT || !interactive()) return true;
                float ratio = client.getWindow().getGuiScale();
                float x = (float) event.x() * ratio, y = (float) event.y() * ratio;
                if (bounds == null || !bounds.contains(x, y)) return true;
                captured = true;
                view.move(x, y);
                view.press(x, y);
                receive();
                return false;
            });
            ScreenMouseEvents.allowMouseRelease(screen).register((current, event) -> {
                if (event.button() != InputConstants.MOUSE_BUTTON_LEFT || !captured) return true;
                captured = false;
                if (interactive()) {
                    float ratio = client.getWindow().getGuiScale();
                    float x = (float) event.x() * ratio, y = (float) event.y() * ratio;
                    view.move(x, y);
                    view.release(x, y);
                    receive();
                } else if (visible) {
                    cancelPointer();
                }
                return false;
            });
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> dispose());
    }

    private boolean interactive() {
        var client = Minecraft.getInstance();
        return visible && chatPointerAvailable(client) && System.nanoTime() - renderedAt < 400_000_000L;
    }

    private static boolean chatPointerAvailable(Minecraft client) {
        return client.gui.screen() instanceof ChatScreen && client.gui.overlay() == null
                && !client.mouseHandler.isMouseGrabbed();
    }

    private void render(GuiGraphicsExtractor graphics) {
        var client = Minecraft.getInstance();
        var config = manager.getConfig();
        if ((!config.isHudEnabled() && !config.isMediaPlayerEnabled()) || client.player == null) {
            if (visible) dispose();
            return;
        }
        if (!view.ready()) {
            renderFallback(graphics, client, config);
            return;
        }
        visible = true;
        var window = client.getWindow();
        view.resize(window.getWidth(), window.getHeight(), 1F);
        if (controller.getCoverGeneration() != coverGeneration) {
            coverGeneration = controller.getCoverGeneration();
            byte[] cover = controller.getCoverPng();
            coverAsset = cover == null ? "" : "assets/island-cover-" + (coverGeneration & 1) + ".png";
            if (cover != null) view.registerImage(coverAsset, cover);
        }

        JsonObject data = controller.viewState();
        if (config.isMediaPlayerEnabled() && config.getAnimatedLyrics().isEnabled() && controller.isActive())
            data.addProperty("lyricsStatus", lyricsStatus());
        boolean chatPointer = chatPointerAvailable(client);
        data.addProperty("cover", coverAsset);
        data.addProperty("hud", config.isHudEnabled());
        data.addProperty("version", VERSION);
        data.addProperty("interactive", chatPointer);
        data.addProperty("media", config.isMediaPlayerEnabled());
        data.addProperty("light", config.getClickGuiTheme() == 4);
        data.addProperty("glass", (!config.isHudEnabled() || config.isHudElementGlass("hud_watermark"))
                && (!config.isMediaPlayerEnabled() || config.isHudElementGlass("media_player_hud")));
        data.addProperty("animated", config.isMediaPlayerAnimated());
        data.addProperty("accent", config.getClickGuiAccent());
        data.addProperty("scale", islandScale(config) * Math.clamp(window.getWidth() / 1920F, .8F, 1.5F));
        data.addProperty("placement", config.getMediaPlayerCorner());
        data.addProperty("custom", config.isMediaPlayerPositionCustom());
        data.addProperty("x", config.getMediaPlayerX() * window.getGuiScale());
        data.addProperty("username", config.isHudEnabled() ? OrionNameProtect.replace(client.getUser().getName()) : "");
        data.addProperty("fps", config.isHudEnabled() ? client.getFps() : 0);
        data.addProperty("ping", ping(client));
        data.add("labels", labels());
        if (!data.equals(previous)) {
            view.update(data);
            previous = data;
        }
        if (chatPointer) {
            view.move((float) client.mouseHandler.getScaledXPos(window) * window.getGuiScale(),
                    (float) client.mouseHandler.getScaledYPos(window) * window.getGuiScale());
        } else {
            if (captured) cancelPointer();
            captured = false;
            view.move(-1000, -1000);
        }
        view.frame();
        if (view.draw(graphics, 0, 0, graphics.guiWidth(), graphics.guiHeight(), 1F / window.getGuiScale(), 1F / window.getGuiScale())) {
            renderedAt = System.nanoTime();
        }
        receive();
    }

    private JsonObject labels() {
        String selected = OrionGuiLanguage.language();
        if (selected.equals(language)) return labels;
        language = selected;
        labels = new JsonObject();
        for (String label : new String[]{"Сейчас играет", "На паузе", "Музыка не играет", "Открой музыку в своём плеере",
                "Твоя музыка — рядом", "Включить MediaPlayer", "Перемешать", "Предыдущий трек", "Следующий трек",
                "Воспроизвести", "Пауза", "Повтор", "Громкость", "Громкость приложения", "Перемотка", "Настройки", "Громкость недоступна",
                "Плеер не выполнил команду", "Управление плеером недоступно", "Громкость этого приложения недоступна",
                "Подожди выполнения предыдущей команды", "Перемести шторку за заголовок",
                "Ищем синхронизированный текст…", "Синхронизированный текст не найден", "Доступен текст без таймкодов",
                "Инструментальная композиция", "Для поиска нужны название и исполнитель", "Источник текста недоступен · повторим позже",
                "Источник текста занят · ожидаем повтора", "Пословная синхронизация", "Построчная синхронизация"}) {
            labels.addProperty(label, OrionGuiLanguage.translate(label));
        }
        return labels;
    }

    private String lyricsStatus() {
        var snapshot = controller.lyricsSnapshot();
        if (!controller.matchesLyrics(snapshot)) return "Ищем синхронизированный текст…";
        if (snapshot.revision() == lyricsRevision) return lyricsStatus;
        lyricsRevision = snapshot.revision();
        lyricsStatus = switch (snapshot.lyrics().status()) {
            case "ready" -> snapshot.lyrics().lines().stream().anyMatch(line -> !line.words().isEmpty())
                    ? "Пословная синхронизация" : "Построчная синхронизация";
            case "missing" -> "Синхронизированный текст не найден";
            case "unsynced" -> "Доступен текст без таймкодов";
            case "instrumental" -> "Инструментальная композиция";
            case "metadata" -> "Для поиска нужны название и исполнитель";
            case "offline" -> "Источник текста недоступен · повторим позже";
            case "rate_limited" -> "Источник текста занят · ожидаем повтора";
            default -> "Ищем синхронизированный текст…";
        };
        return lyricsStatus;
    }

    private void renderFallback(GuiGraphicsExtractor graphics, Minecraft client, OrionConfig config) {
        String label = "GleamClient";
        if (config.isHudEnabled()) {
            int latency = ping(client);
            label += " " + VERSION + " · " + OrionNameProtect.replace(client.getUser().getName())
                    + " · " + client.getFps() + " FPS · " + (latency < 0 ? "—" : latency) + " ms";
        }
        int screenWidth = graphics.guiWidth();
        float scale = Math.min(islandScale(config), Math.min(screenWidth / 13F, graphics.guiHeight() / 30F));
        if (scale <= 0) return;
        int maxWidth = Math.max(1, Math.min(280, (int) (screenWidth / scale) - 12));
        label = client.font.plainSubstrByWidth(label, maxWidth);
        String track = config.isMediaPlayerEnabled() && controller.isActive()
                ? client.font.plainSubstrByWidth(controller.getTitle(), maxWidth) : "";
        int width = Math.max(client.font.width(label), client.font.width(track)) + 12;
        int scaledWidth = (int) Math.ceil(width * scale);
        int x = config.isMediaPlayerPositionCustom() ? config.getMediaPlayerX() : switch (config.getMediaPlayerCorner()) {
            case "TOP_LEFT" -> 5;
            case "TOP_RIGHT" -> screenWidth - scaledWidth - 5;
            default -> (screenWidth - scaledWidth) / 2;
        };
        var pose = graphics.pose();
        pose.pushMatrix();
        try {
            pose.translate(Math.clamp(x, 0, Math.max(0, screenWidth - scaledWidth)), 0);
            pose.scale(scale);
            graphics.fill(0, 0, width, track.isBlank() ? 17 : 30, 0xDB171A23);
            graphics.text(client.font, label, 6, 4, 0xFFF3F4FA);
            if (!track.isBlank()) graphics.text(client.font, track, 6, 17, 0xFFC2D2F7);
        } finally {
            pose.popMatrix();
        }
    }

    private static float islandScale(OrionConfig config) {
        return config.getHudScale() * config.getHudElementScale("media");
    }

    private static int ping(Minecraft client) {
        var connection = client.getConnection();
        var info = connection == null ? null : connection.getPlayerInfo(client.player.getUUID());
        return info == null ? -1 : info.getLatency();
    }

    private void cancelPointer() {
        view.cancelPointer();
        view.release(-1000, -1000);
    }

    private void receive() {
        byte[] messages = view.messages();
        if (messages == null || messages.length > 32768) return;
        for (String line : new String(messages, StandardCharsets.UTF_8).replace('\0', '\n').lines().toList()) {
            if (line.isBlank()) continue;
            try {
                action(JsonParser.parseString(line).getAsJsonObject());
            } catch (RuntimeException invalid) {
                LoggerFactory.getLogger(OrionClient.MOD_ID).debug("Invalid HUD island action", invalid);
            }
        }
    }

    private void action(JsonObject event) {
        String action = event.get("action").getAsString();
        if ("bounds".equals(action)) {
            float x = event.get("x").getAsFloat(), y = event.get("y").getAsFloat();
            float w = event.get("width").getAsFloat(), h = event.get("height").getAsFloat(), r = event.get("radius").getAsFloat();
            if (Float.isFinite(x + y + w + h + r) && w > 0 && h > 0 && r >= 0) bounds = new Bounds(x, y, w, h, r);
            return;
        }
        if (!interactive()) return;
        var client = Minecraft.getInstance();
        if ("move".equals(action)) {
            float x = event.get("x").getAsFloat();
            if (!Float.isFinite(x)) return;
            manager.getConfig().setMediaPlayerPositionCustom(true);
            manager.setMediaPlayerPosition(Math.clamp(Math.round(x / client.getWindow().getGuiScale()),
                    0, client.getWindow().getGuiScaledWidth()), 0);
        } else if ("settings".equals(action)) {
            client.gui.setScreen(OrionClickGuiScreen.withMediaPlayerSettings(manager));
        } else if ("enable".equals(action)) {
            manager.setMediaPlayerEnabled(true);
        } else {
            if (!manager.getConfig().isMediaPlayerEnabled() || !event.has("source")
                    || !event.get("source").getAsString().equals(controller.getSource())) return;
            switch (action) {
                case "play" -> controller.playPause();
                case "previous" -> controller.previous();
                case "next" -> controller.next();
                case "shuffle" -> controller.toggleShuffle();
                case "repeat" -> controller.cycleRepeat();
                case "seek" -> controller.seek(event.get("value").getAsDouble());
                case "volume" -> controller.setVolume(event.get("value").getAsFloat(), event.has("final") && event.get("final").getAsBoolean());
                default -> { }
            }
        }
    }

    private void dispose() {
        view.dispose();
        previous = null;
        bounds = null;
        captured = visible = false;
        coverGeneration = -1;
        coverAsset = "";
        lyricsRevision = -1;
    }

    private static byte[] loadDocument() {
        try {
            for (String face : new String[]{"Regular", "Bold"}) {
                LumenNative.registerFont(bytes("assets/orion-" + face.toLowerCase(Locale.ROOT) + ".ttf"),
                        read(ROOT + "fonts/paradox/ParadoxSans-" + face + ".ttf"));
            }
            LumenNative.registerImage(bytes("assets/gleam-hud-badge.png"), read(ROOT + "textures/cosmetics/orion_badge.png"));
            if (document == null) {
                String html = new String(read(ROOT + "html/hud-island/index.html"), StandardCharsets.UTF_8);
                String css = new String(read(ROOT + "html/hud-island/style.css"), StandardCharsets.UTF_8);
                String js = new String(read(ROOT + "html/hud-island/island.js"), StandardCharsets.UTF_8);
                document = bytes(html.replace("<link rel=\"stylesheet\" href=\"style.css\">", "<style>" + css + "</style>")
                        .replace("<script src=\"island.js\"></script>", "<script>" + js + "</script>"));
            }
            return document;
        } catch (IOException failure) { throw new UncheckedIOException(failure); }
    }

    private static byte[] read(String resource) throws IOException {
        try (var stream = OrionMediaPlayerHud.class.getResourceAsStream(resource)) {
            if (stream == null) throw new IOException("Missing HUD resource " + resource);
            return stream.readAllBytes();
        }
    }
    private static byte[] bytes(String text) { return text.getBytes(StandardCharsets.UTF_8); }
}
