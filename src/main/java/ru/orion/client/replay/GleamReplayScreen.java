//~ legacy_gui
package ru.orion.client.replay;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import com.paradox.lumen.LumenNative;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import org.slf4j.LoggerFactory;
import ru.orion.client.GleamMinecraft;
import ru.orion.client.gui.OrionLumenView;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Hosts the shared replay HTML in Minecraft's screen stack. */
final class GleamReplayScreen extends Screen {
    private static final String ROOT = "/assets/orion-client/";
    private static final String FONTS = ROOT + "fonts/paradox/ParadoxSans-";
    private static final String MARKER = "assets/gleam-replay-video.png";
    private static final Gson JSON = new Gson();
    private static final Pattern SVG_SYMBOL = Pattern.compile("<symbol id=\"(i-[^\"]+)\" viewBox=\"0 0 24 24\">(.*?)</symbol>", Pattern.DOTALL);
    private static final Pattern SVG_USE = Pattern.compile("<svg([^>]*)><use href=\"#(i-[^\"]+)\"\\s*/></svg>");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault());
    private final Screen parent;
    private final GleamReplay replay;
    private final OrionLumenView view = new OrionLumenView("replay/pause-preview.html", "replay_screen", FONTS + "Regular.ttf", this::document);
    private final Set<String> thumbnails = new HashSet<>(), thumbnailRequests = new HashSet<>();
    private List<GleamReplay.Clip> sentClips;
    private GleamReplay.Preferences sentSettings;
    private JsonObject previous;
    private GleamReplay.Clip clip;
    private ReplayPlayback playback;
    private DynamicTexture video;
    private int videoId;
    private float scale = 1;
    private int layoutWidth, layoutHeight;
    private long nextStateUpdate, lastPlayerUpdate;
    private double position, rate = 1, volume = 1;
    private boolean playing, ended, seeking, muted, ready;

    GleamReplayScreen(Screen parent, GleamReplay replay) {
        super(Component.translatable(GleamReplay.LABEL));
        this.parent = parent;
        this.replay = replay;
    }

    @Override public boolean isPauseScreen() { return true; }

    private byte[] document() {
        try {
            for (String face : List.of("Regular", "SemiBold", "Bold"))
                LumenNative.registerFont(bytes("assets/gleam-replay-" + face + ".ttf"), resource(FONTS + face + ".ttf"));
            var marker = new ByteArrayOutputStream();
            ImageIO.write(new BufferedImage(16, 9, BufferedImage.TYPE_INT_RGB), "png", marker);
            videoId = LumenNative.registerImage(bytes(MARKER), marker.toByteArray());
            String script = new String(resource(ROOT + "html/replay/replay-preview.js"), StandardCharsets.UTF_8);
            String page = new String(resource(ROOT + "html/replay/pause-preview.html"), StandardCharsets.UTF_8)
                    .replace("/src/main/resources/assets/orion-client/fonts/paradox/ParadoxSans-", "assets/gleam-replay-")
                    .replace("dvh", "vh")
                    .replace("<dialog class=\"replay-screen\"", "<div class=\"replay-screen\"")
                    .replace("</dialog>", "</div>")
                    .replace("<template id=\"clip-template\">", "<div id=\"clip-template\" hidden>")
                    .replace("</template>", "</div>");
            return bytes(replayDocument(page, script));
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }

    private static String replayDocument(String page, String script) {
        var icons = new JsonObject();
        SVG_SYMBOL.matcher(page).results().forEach(symbol -> icons.addProperty(symbol.group(1), symbol.group(2)));
        // The game renderer can paint inline SVG geometry without resolving a symbol reference.
        String expanded = SVG_USE.matcher(page).replaceAll(use -> {
            var geometry = icons.get(use.group(2));
            if (geometry == null) return Matcher.quoteReplacement(use.group());
            return Matcher.quoteReplacement("<svg" + use.group(1) + " viewBox=\"0 0 24 24\">" + geometry.getAsString() + "</svg>");
        });
        return expanded.replace("<script src=\"replay-preview.js\"></script>",
                "<script>window.gleamReplayInGame=true;window.gleamReplayIcons=" + JSON.toJson(icons)
                        + ";\n" + script + "</script>");
    }

    private static byte[] resource(String path) throws IOException {
        try (var input = GleamReplayScreen.class.getResourceAsStream(path)) {
            if (input == null) throw new IOException(path);
            return input.readAllBytes();
        }
    }

    private static byte[] bytes(String value) { return value.getBytes(StandardCharsets.UTF_8); }

    //? if >=26.1 {
    @Override public void extractBackground(GuiGraphicsExtractor graphics, int x, int y, float delta) { }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int x, int y, float delta) {
    //?} else {
    /*@Override public void renderBackground(GuiGraphicsExtractor graphics, int x, int y, float delta) { }

    @Override public void render(GuiGraphicsExtractor graphics, int x, int y, float delta) {
    *///?}
        if (!view.ready()) {
            if (minecraft != null) minecraft.execute(() -> {
                if (GleamMinecraft.screen(minecraft) != this) return;
                if (minecraft.player != null)
                    GleamMinecraft.setOverlayMessage(minecraft, Component.literal("Интерфейс Lumen недоступен"), false);
                onClose();
            });
            return;
        }
        ready = true;
        scale = 1F / minecraft.getWindow().getGuiScale();
        layoutWidth = (int)Math.ceil(width / scale);
        layoutHeight = (int)Math.ceil(height / scale);
        long now = System.nanoTime();
        if (view.resize(layoutWidth, layoutHeight, 1F) || now >= nextStateUpdate) {
            updateState();
            nextStateUpdate = now + 100_000_000;
        }
        updateVideo();
        view.frame();
        receive();
        if (ready) view.draw(graphics, 0, 0, width, height, scale, scale);
    }

    private void updateState() {
        var next = new JsonObject();
        next.addProperty("width", layoutWidth);
        next.addProperty("height", layoutHeight);
        next.addProperty("buffer", replay.buffered());
        next.addProperty("inWorld", replay.inWorld());
        next.addProperty("recording", replay.recording());
        next.addProperty("pending", replay.pending());
        next.addProperty("recordSeconds", replay.recording() ? replay.recordingSeconds() : 0);
        next.addProperty("version", replay.messageVersion());
        next.addProperty("message", replay.message());
        boolean changed = !next.equals(previous);
        previous = next.deepCopy();
        var settings = replay.preferences();
        if (settings != sentSettings) {
            next.add("settings", JSON.toJsonTree(settings));
            sentSettings = settings;
            changed = true;
        }
        var clips = replay.clips();
        if (clips != sentClips) {
            var entries = new com.google.gson.JsonArray();
            for (var item : clips) {
                JsonObject entry = JSON.toJsonTree(item).getAsJsonObject();
                entry.addProperty("at", DATE.format(Instant.ofEpochMilli(item.at())));
                if (thumbnails.contains(thumbnail(item))) entry.addProperty("thumbnail", thumbnail(item));
                entries.add(entry);
            }
            next.add("clips", entries);
            sentClips = clips;
            changed = true;
        }
        if (changed) view.update(next);
    }

    private static String thumbnail(GleamReplay.Clip item) {
        return "assets/gleam-replay-" + item.id() + "-" + item.revision() + ".png";
    }

    private void loadThumbnail(GleamReplay.Clip item) {
        if (item == null) return;
        String asset = thumbnail(item);
        if (!thumbnailRequests.add(asset)) return;
        replay.readThumbnail(item, image -> minecraft.execute(() -> {
            if (!ready || GleamMinecraft.screen(minecraft) != this) return;
            if (image == null) return;
            LumenNative.registerImage(bytes(asset), image);
            thumbnails.add(asset);
            var state = new JsonObject();
            state.addProperty("thumbnailId", item.id());
            state.addProperty("revision", item.revision());
            state.addProperty("thumbnail", asset);
            view.update(state);
        }));
    }

    private void receive() {
        byte[] raw = view.messages();
        if (raw == null || raw.length == 0 || raw.length > 32_768) return;
        for (String line : new String(raw, StandardCharsets.UTF_8).replace('\0', '\n').lines().toList()) {
            try {
                var value = JsonParser.parseString(line);
                if (value.isJsonArray()) for (var event : value.getAsJsonArray()) {
                    dispatch((event.isJsonPrimitive() ? JsonParser.parseString(event.getAsString()) : event).getAsJsonObject());
                } else if (value.isJsonObject()) dispatch(value.getAsJsonObject());
            } catch (RuntimeException error) {
                LoggerFactory.getLogger("GleamReplay").debug("Invalid replay UI action", error);
            }
        }
    }

    private void dispatch(JsonObject event) {
        if (!ready) return;
        String action = event.get("action").getAsString();
        var values = event.getAsJsonObject("values");
        switch (action) {
            case "close" -> onClose();
            case "buffer", "record", "save", "settings", "bindings", "favorite", "trim", "remove", "folder" -> replay.submit(action, values);
            case "thumbnail" -> loadThumbnail(find(values));
            case "load-preview" -> {
                closeVideo();
                clip = find(values);
                if (clip == null) return;
                position = 0;
                ended = seeking = false;
                rate = speed(values.get("rate").getAsDouble());
                setAudio(values);
                openPlayback();
                playerState();
            }
            case "unload-preview" -> closeVideo();
            case "play-preview" -> {
                if (playback == null && !openPlayback()) { playerState(); return; }
                playing = true;
                if (ended || position >= clip.seconds()) {
                    position = 0;
                    seeking = true;
                    playback.seek(0, true);
                } else playback.play();
                ended = false;
                playerState();
            }
            case "pause-preview" -> {
                if (playback == null) return;
                playback.pause();
                playing = false;
                playerState();
            }
            case "seek" -> {
                if (playback == null) return;
                double next = values.get("position").getAsDouble();
                if (!Double.isFinite(next)) return;
                position = Math.clamp(next, 0, clip.seconds());
                ended = position >= clip.seconds();
                if (ended) playing = false;
                seeking = true;
                playback.seek(Math.min(position, Math.max(0, clip.seconds() - .04)), playing);
                playerState();
            }
            case "speed" -> {
                rate = speed(values.get("value").getAsDouble());
                if (playback != null) playback.rate(rate);
            }
            case "audio-preview" -> setAudio(values);
            case "copy-title" -> {
                if (clip != null) { minecraft.keyboardHandler.setClipboard(clip.title()); toast("Имя скопировано"); }
            }
            case "still" -> saveFrame();
        }
    }

    private GleamReplay.Clip find(JsonObject values) {
        String id = values.get("id").getAsString();
        return replay.clips().stream().filter(item -> item.id().equals(id)).findFirst().orElse(null);
    }

    private static double speed(double value) { return value == .25 || value == .5 || value == 2 ? value : 1; }

    private void setAudio(JsonObject values) {
        double next = values.get("volume").getAsDouble();
        if (Double.isFinite(next)) volume = Math.clamp(next, 0, 1);
        muted = values.get("muted").getAsBoolean();
        if (playback != null) playback.volume(muted ? 0 : volume);
    }

    private boolean openPlayback() {
        var file = clip == null ? null : replay.media(clip.id());
        if (file == null) { toast("Файл клипа недоступен"); return false; }
        int width = Math.clamp(layoutWidth, 640, ReplayPlayback.MAX_WIDTH);
        int height = Math.clamp(layoutHeight, 360, ReplayPlayback.MAX_HEIGHT);
        playback = new ReplayPlayback(replay, file, rate, muted ? 0 : volume, width, height);
        if (position > 0 && !ended) playback.seek(position, false);
        return true;
    }

    private void stopPlayback() {
        if (playback != null) playback.close();
        playback = null;
        playing = seeking = false;
    }

    private void closeVideo() {
        stopPlayback();
        if (video != null) video.close();
        view.liveImage(videoId, null);
        video = null;
        clip = null;
    }

    private void updateVideo() {
        if (playback == null) return;
        var frame = playback.frame();
        boolean firstFrame = video == null;
        if (frame != null) {
            NativeImage pixels = frame.pixels();
            if (video == null || video.getPixels().getWidth() != pixels.getWidth() || video.getPixels().getHeight() != pixels.getHeight()) {
                if (video != null) video.close();
                video = new DynamicTexture(() -> "replay video", pixels);
                view.liveImage(videoId, video);
            } else { video.setPixels(pixels); video.upload(); }
        }
        if (playback.failed()) {
            stopPlayback();
            toast("Не удалось воспроизвести клип");
            playerState();
            return;
        }
        var status = playback.status();
        if (status == null) {
            if (firstFrame && frame != null) playerState();
            return;
        }
        boolean changed = seeking != status.seeking() || playing != status.playing() || status.ended() && !ended;
        double previous = position;
        seeking = status.seeking();
        playing = status.playing();
        ended |= status.ended();
        if (ended) position = clip.seconds();
        else if (!seeking) position = Math.clamp(status.position(), 0, clip.seconds());
        if (changed || firstFrame && frame != null || position != previous && System.nanoTime() - lastPlayerUpdate >= 33_000_000)
            playerState();
    }

    private void playerState() {
        if (clip == null || !ready) return;
        lastPlayerUpdate = System.nanoTime();
        var player = new JsonObject();
        player.addProperty("id", clip.id());
        player.addProperty("position", position);
        player.addProperty("playing", playing);
        player.addProperty("ended", ended);
        player.addProperty("seeking", seeking);
        player.addProperty("hasFrame", video != null);
        var state = new JsonObject();
        state.add("player", player);
        view.update(state);
    }

    private void toast(String text) {
        if (!ready) return;
        var state = new JsonObject();
        state.addProperty("toast", text);
        view.update(state);
    }

    private void saveFrame() {
        if (video == null) { toast("Кадр ещё загружается"); return; }
        var pixels = video.getPixels();
        var copy = new NativeImage(pixels.getWidth(), pixels.getHeight(), false);
        copy.copyFrom(pixels);
        var file = minecraft.gameDirectory.toPath().resolve("screenshots").resolve("Replay-" + UUID.randomUUID() + ".png");
        Thread.startVirtualThread(() -> {
            try (copy) {
                Files.createDirectories(file.getParent());
                copy.writeToFile(file);
                minecraft.execute(() -> toast("Кадр сохранён в screenshots"));
            } catch (IOException error) { minecraft.execute(() -> toast("Не удалось сохранить кадр")); }
        });
    }

    @Override public void mouseMoved(double x, double y) {
        if (ready) view.move((float) x / scale, (float) y / scale);
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!ready || event.button() != InputConstants.MOUSE_BUTTON_LEFT) return false;
        view.press((float) event.x() / scale, (float) event.y() / scale);
        receive();
        return true;
    }

    @Override public boolean mouseReleased(MouseButtonEvent event) {
        if (!ready || event.button() != InputConstants.MOUSE_BUTTON_LEFT) return false;
        view.release((float) event.x() / scale, (float) event.y() / scale);
        receive();
        return true;
    }

    @Override public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        mouseMoved(event.x(), event.y());
        return ready;
    }

    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (!ready) return false;
        view.wheel((float) x / scale, (float) y / scale, (float) horizontal, (float) vertical);
        receive();
        return true;
    }

    @Override public boolean keyPressed(KeyEvent event) { return key(event, true) || super.keyPressed(event); }
    @Override public boolean keyReleased(KeyEvent event) { return key(event, false) || super.keyReleased(event); }

    private boolean key(KeyEvent event, boolean down) {
        if (!ready) return false;
        if (down && !event.hasAltDown() && event.hasControlDownWithQuirk()) {
            if (event.key() == InputConstants.KEY_A) editInput("select", "");
            if (event.key() == InputConstants.KEY_V) editInput("type", minecraft.keyboardHandler.getClipboard());
        }
        String code = GleamReplay.browserKey(event.key());
        if (code == null) code = switch (event.key()) {
            case InputConstants.KEY_ESCAPE -> "Escape";
            case InputConstants.KEY_TAB -> "Tab";
            case InputConstants.KEY_BACKSPACE -> "Backspace";
            default -> "";
        };
        if (code.isEmpty()) return false;
        String key = code.startsWith("Key") ? code.substring(3).toLowerCase(java.util.Locale.ROOT)
                : code.startsWith("Digit") ? code.substring(5)
                : switch (code) { case "Space" -> " "; case "NumpadEnter" -> "Enter"; default -> code; };
        int modifiers = (event.hasShiftDown() ? 1 : 0) | (event.hasControlDownWithQuirk() ? 2 : 0)
                | (event.hasAltDown() ? 4 : 0) | ((event.modifiers() & InputConstants.MOD_SUPER) != 0 ? 8 : 0);
        view.key(key, code, modifiers, down);
        receive();
        return true;
    }

    @Override public boolean charTyped(CharacterEvent event) {
        if (!ready) return false;
        if (event.isAllowedChatCharacter()) editInput("type", event.codepointAsString());
        receive();
        return true;
    }

    private void editInput(String command, String text) {
        var shortcut = new JsonObject();
        shortcut.addProperty("command", command);
        shortcut.addProperty("text", text.substring(0, Math.min(24, text.length())));
        view.editShortcut(shortcut);
    }

    @Override public void onClose() { GleamMinecraft.setScreen(minecraft, parent); }

    @Override public void removed() {
        ready = false;
        closeVideo();
        view.dispose();
        super.removed();
    }
}
