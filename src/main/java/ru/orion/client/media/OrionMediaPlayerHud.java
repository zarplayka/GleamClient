package ru.orion.client.media;

import ru.orion.client.GleamMinecraft;

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
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.slf4j.LoggerFactory;
import ru.orion.client.OrionClient;
import ru.orion.client.config.OrionConfig;
import ru.orion.client.config.OrionConfigManager;
import ru.orion.client.effect.OrionNameProtect;
import ru.orion.client.gui.OrionClickGuiScreen;
import ru.orion.client.gui.OrionGuiLanguage;
import ru.orion.client.gui.OrionLumenView;
import ru.orion.client.hud.OrionHudDrag;
import ru.orion.client.hud.OrionHudEditorScreen;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/** Мост острова HUD: HTML/CSS/JS через Lumen, данные и команды остаются в менеджерах Java. */
public final class OrionMediaPlayerHud {
    private static final String ROOT = "/assets/orion-client/";
    private static final String VERSION = FabricLoader.getInstance().getModContainer(OrionClient.MOD_ID)
            .map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse("");
    private static byte[] document;
    private final OrionConfigManager manager;
    private final OrionMediaController controller;
    private final OrionHudDrag drag = new OrionHudDrag("island");
    private final OrionLumenView view = new OrionLumenView("hud-island/index.html", "hud_island",
            ROOT + "fonts/paradox/ParadoxSans-Regular.ttf", OrionMediaPlayerHud::loadDocument);
    private JsonObject previous;
    private JsonObject labels;
    private String language;
    private Bounds bounds;
    private Bounds portrait;
    private boolean captured, visible, fallbackHud;
    private long renderedAt, lastCompactFrame;
    private int lastFrameWidth, lastFrameHeight, lastFrameGuiScale;
    private float collapsedWidth;
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
                    if (!(GleamMinecraft.screen(Minecraft.getInstance()) instanceof ChatScreen)) drawIsland(graphics);
                });
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!(screen instanceof ChatScreen)) return;
            ScreenMouseEvents.allowMouseClick(screen).register((current, event) -> {
                if (event.button() != InputConstants.MOUSE_BUTTON_LEFT || !interactive()) return true;
                if (OrionHudEditorScreen.editingIsland()
                        || OrionHudEditorScreen.claimsIslandPointer(event.x(), event.y())) return true;
                float ratio = client.getWindow().getGuiScale();
                float x = (float) event.x() * ratio, y = (float) event.y() * ratio;
                if (bounds == null || !bounds.contains(x, y)) return true;
                if (OrionHudEditorScreen.editing(client) && OrionHudEditorScreen.islandHeader(y, bounds.y, bounds.width, bounds.height)) return true;
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

    public void registerChatRendering() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (screen instanceof ChatScreen) {
                ScreenEvents.afterExtract(screen).register((current, graphics, x, y, delta) -> drawIsland(graphics));
            }
        });
    }

    private boolean interactive() {
        var client = Minecraft.getInstance();
        return visible && (manager.getConfig().isHudEnabled() || manager.getConfig().isMediaPlayerEnabled())
                && chatPointerAvailable(client) && System.nanoTime() - renderedAt < 400_000_000L;
    }

    private static boolean chatPointerAvailable(Minecraft client) {
        return GleamMinecraft.screen(client) instanceof ChatScreen && GleamMinecraft.overlay(client) == null
                && !client.mouseHandler.isMouseGrabbed();
    }

    private void drawIsland(GuiGraphicsExtractor graphics) {
        OrionHudDrag.draw("island", graphics, () -> render(graphics));
    }

    private void render(GuiGraphicsExtractor graphics) {
        var client = Minecraft.getInstance();
        var config = manager.getConfig();
        boolean enabled = config.isHudEnabled() || config.isMediaPlayerEnabled();
        if ((!enabled && !OrionHudDrag.fadingOut("island")) || client.player == null) {
            if (visible) dispose();
            else OrionHudDrag.forget("island");
            return;
        }
        if (!view.ready()) {
            renderFallback(graphics, client, config);
            return;
        }
        if (!enabled && previous != null) {
            view.draw(graphics, 0, 0, graphics.guiWidth(), graphics.guiHeight(),
                    1F / client.getWindow().getGuiScale(), 1F / client.getWindow().getGuiScale(), OrionHudDrag.opacity());
            if (previous.get("hud").getAsBoolean()) drawPortrait(graphics, client);
            return;
        }
        visible = true;
        var window = client.getWindow();
        int width = window.getWidth(), height = window.getHeight(), ratio = window.getGuiScale();
        boolean layoutEditing = OrionHudEditorScreen.editingIsland();
        boolean chatPointer = chatPointerAvailable(client) && !layoutEditing;
        boolean wasInteractive = previous != null && previous.get("interactive").getAsBoolean();
        long now = System.nanoTime();
        boolean compactCached = !chatPointer && !wasInteractive && !OrionHudEditorScreen.editing(client)
                && previous != null && previous.get("hud").getAsBoolean() == config.isHudEnabled()
                && previous.get("media").getAsBoolean() == config.isMediaPlayerEnabled()
                && width == lastFrameWidth && height == lastFrameHeight && ratio == lastFrameGuiScale
                && now - lastCompactFrame < 50_000_000L;
        if (compactCached) {
            drawView(graphics, client, config.isHudEnabled());
            return;
        }
        lastCompactFrame = now;
        lastFrameWidth = width;
        lastFrameHeight = height;
        lastFrameGuiScale = ratio;
        view.resize(window.getWidth(), window.getHeight(), 1F);
        if (controller.getCoverGeneration() != coverGeneration) {
            coverGeneration = controller.getCoverGeneration();
            byte[] cover = controller.getCoverPng();
            coverAsset = cover == null ? "" : "assets/island-cover-" + (coverGeneration & 1) + ".png";
            if (cover != null) view.registerImage(coverAsset, cover);
        }

        JsonObject data = config.isMediaPlayerEnabled() ? controller.viewState() : new JsonObject();
        if (config.isMediaPlayerEnabled() && config.getAnimatedLyrics().isEnabled() && controller.isActive())
            data.addProperty("lyricsStatus", lyricsStatus());
        data.addProperty("cover", coverAsset);
        data.addProperty("hud", config.isHudEnabled());
        data.addProperty("version", VERSION);
        data.addProperty("interactive", chatPointer);
        data.addProperty("layoutEditing", layoutEditing);
        data.addProperty("media", config.isMediaPlayerEnabled());
        data.addProperty("light", config.getClickGuiTheme() == 4);
        data.addProperty("animated", config.isMediaPlayerAnimated());
        data.addProperty("accent", config.getClickGuiAccent());
        data.addProperty("scale", islandScale(config) * Math.clamp(Math.min(width / 1920F, height / 1080F), .55F, 1.5F));
        data.addProperty("placement", config.getMediaPlayerCorner());
        data.addProperty("custom", config.isMediaPlayerPositionCustom());
        var position = islandPosition(graphics, config, 0);
        data.addProperty("x", position.x() * ratio);
        data.addProperty("y", position.y() * ratio);
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
            if (wasInteractive) view.move(-1000, -1000);
        }
        view.frame();
        drawView(graphics, client, config.isHudEnabled());
    }

    private void drawView(GuiGraphicsExtractor graphics, Minecraft client, boolean hud) {
        float scale = client.getWindow().getGuiScale();
        if (view.draw(graphics, 0, 0, graphics.guiWidth(), graphics.guiHeight(),
                1F / scale, 1F / scale, OrionHudDrag.opacity())) {
            renderedAt = System.nanoTime();
        }
        receive();
        if (hud) drawPortrait(graphics, client);
        if (bounds != null) {
            int offset = Math.round((bounds.width - collapsedWidth) / (2F * scale));
            OrionHudDrag.observe("island", Math.round(bounds.x / scale), Math.round(bounds.y / scale),
                    Math.round(bounds.width / scale), Math.round(bounds.height / scale),
                    drag.tracked(manager.getConfig(), graphics.guiWidth(), graphics.guiHeight(),
                            (x, y) -> setPosition(x + offset, y)), "media");
        }
    }

    private OrionHudDrag.Position islandPosition(GuiGraphicsExtractor graphics, OrionConfig config, int defaultX) {
        return drag.restore(config, graphics.guiWidth(), graphics.guiHeight(),
                config.isMediaPlayerPositionCustom() ? config.getMediaPlayerX() : -1,
                config.getExtraHudPosition("island", 1), defaultX, 0);
    }

    private void drawPortrait(GuiGraphicsExtractor graphics, Minecraft client) {
        if (portrait == null || OrionHudDrag.opacity() <= 0F) return;
        float ratio = client.getWindow().getGuiScale();
        var pose = graphics.pose();
        pose.pushMatrix();
        try {
            pose.translate(portrait.x / ratio, portrait.y / ratio).scale(portrait.width / (8F * ratio));
            drawHead(graphics, client, 0, 0);
        } finally {
            pose.popMatrix();
        }
    }

    private static void drawHead(GuiGraphicsExtractor graphics, Minecraft client, int x, int y) {
        var player = client.player;
        var skin = player.getSkin().body().texturePath();
        int color = OrionHudDrag.tint(-1);
        graphics.blit(RenderPipelines.GUI_TEXTURED, skin, x, y, 8, 8, 8, 8, 8, 8, 64, 64, color);
        if (player.isModelPartShown(net.minecraft.world.entity.player.PlayerModelPart.HAT)) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, skin, x, y, 40, 8, 8, 8, 8, 8, 64, 64, color);
        }
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
        boolean hud = config.isHudEnabled() || OrionHudDrag.fadingOut("island") && fallbackHud;
        if (OrionHudDrag.opacity() < .015F) return;
        fallbackHud = hud;
        String brand = hud ? "GleamClient " + VERSION : "GleamClient";
        int latency = ping(client);
        String details = hud ? OrionNameProtect.replace(client.getUser().getName())
                + " · " + client.getFps() + " FPS · " + (latency < 0 ? "—" : latency) + " ms" : "";
        int screenWidth = graphics.guiWidth();
        float scale = Math.min(islandScale(config), Math.min(screenWidth / 13F, graphics.guiHeight() / 30F));
        if (scale <= 0) return;
        int maxWidth = Math.max(1, Math.min(280, (int) (screenWidth / scale) - 12));
        int detailsX = client.font.width(brand) + 19;
        details = client.font.plainSubstrByWidth(details, Math.max(0, maxWidth - detailsX));
        String track = config.isMediaPlayerEnabled() && controller.isActive()
                ? client.font.plainSubstrByWidth(controller.getTitle(), maxWidth) : "";
        int width = Math.max(client.font.width(track) + 12, hud ? detailsX + client.font.width(details) + 6
                : client.font.width(brand) + 12);
        int scaledWidth = (int) Math.ceil(width * scale);
        int defaultX = switch (config.getMediaPlayerCorner()) {
            case "TOP_LEFT" -> 5;
            case "TOP_RIGHT" -> screenWidth - scaledWidth - 5;
            default -> (screenWidth - scaledWidth) / 2;
        };
        int scaledHeight = (int) Math.ceil((track.isBlank() ? 17 : 30) * scale);
        var position = islandPosition(graphics, config, defaultX);
        int x = Math.clamp(position.x(), 0, Math.max(0, screenWidth - scaledWidth));
        int y = Math.clamp(position.y(), 0, Math.max(0, graphics.guiHeight() - scaledHeight));
        OrionHudDrag.observe("island", x, y, scaledWidth, scaledHeight,
                drag.tracked(config, graphics.guiWidth(), graphics.guiHeight(), this::setPosition), "media");
        var pose = graphics.pose();
        pose.pushMatrix();
        try {
            pose.translate(x, y);
            pose.scale(scale);
            graphics.fill(0, 0, width, track.isBlank() ? 17 : 30, OrionHudDrag.tint(0xEB0F1216));
            int ink = OrionHudDrag.tint(0xFFF3F4FA);
            if (hud) drawHead(graphics, client, detailsX - 10, 4);
            //? if >=26.1 {
            graphics.text(client.font, brand, 6, 4, ink);
            if (hud) graphics.text(client.font, details, detailsX, 4, ink);
            //?} else {
            /*graphics.drawString(client.font, brand, 6, 4, ink);
            if (hud) graphics.drawString(client.font, details, detailsX, 4, ink);
            *///?}
            //? if >=26.1 {
            if (!track.isBlank()) graphics.text(client.font, track, 6, 17, OrionHudDrag.tint(0xFF65D7A0));
            //?} else {
            /*if (!track.isBlank()) graphics.drawString(client.font, track, 6, 17, OrionHudDrag.tint(0xFF65D7A0));
            *///?}
        } finally {
            pose.popMatrix();
        }
    }

    private static float islandScale(OrionConfig config) {
        return config.getHudScale() * config.getHudElementScale("media");
    }

    private void setPosition(int x, int y) {
        var config = manager.getConfig();
        config.setMediaPlayerPositionCustom(true);
        config.setMediaPlayerX(x);
        config.setExtraHudPosition("island", x, y);
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
            collapsedWidth = event.has("collapsedWidth") ? event.get("collapsedWidth").getAsFloat() : w;
            return;
        }
        if ("portrait".equals(action)) {
            float x = event.get("x").getAsFloat(), y = event.get("y").getAsFloat(), size = event.get("size").getAsFloat();
            portrait = Float.isFinite(x + y + size) && size > 0 ? new Bounds(x, y, size, size, 0) : null;
            return;
        }
        if (!interactive()) return;
        if ("settings".equals(action)) {
            GleamMinecraft.setScreen(Minecraft.getInstance(), OrionClickGuiScreen.withMediaPlayerSettings(manager));
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
        OrionHudDrag.forget("island");
        view.dispose();
        previous = null;
        bounds = null;
        portrait = null;
        captured = visible = false;
        fallbackHud = false;
        coverGeneration = -1;
        coverAsset = "";
        lyricsRevision = -1;
    }

    private static byte[] loadDocument() {
        try {
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
